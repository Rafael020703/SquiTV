package rsv.squitv.build;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.*;

/** Keep the original Material vectors byte-for-byte, packaging only referenced icon classes.
 * Scans compiled bytecode, so aliases, star imports and shared Compose sources work without
 * maintaining a manual list. This does not shrink/obfuscate the app or touch reflective JNA APIs. */
@CacheableTask
public abstract class PrepareDesktopIcons extends DefaultTask {
    private static final Pattern ICON = Pattern.compile("androidx/compose/material/icons/[A-Za-z0-9_$/]+");
    @InputFile @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getSourceJar();
    @Classpath public abstract ConfigurableFileCollection getCompiledClasses();
    @OutputFile public abstract RegularFileProperty getOutputJar();

    @TaskAction public void prepare() throws Exception {
        Path input = getSourceJar().get().getAsFile().toPath();
        Path output = getOutputJar().get().getAsFile().toPath();
        try (ZipFile source = new ZipFile(input.toFile())) {
            SortedMap<String, ZipEntry> entries = new TreeMap<>();
            source.stream().filter(entry -> !entry.isDirectory()).forEach(entry -> entries.put(entry.getName(), entry));
            Set<String> references = new HashSet<>();
            for (File directory : getCompiledClasses()) {
                if (!directory.isDirectory()) continue;
                try (var paths = Files.walk(directory.toPath())) {
                    for (Path file : paths.filter(path -> path.toString().endsWith(".class")).toList())
                        references.addAll(iconReferences(Files.readAllBytes(file)));
                }
            }
            SortedSet<String> retained = new TreeSet<>();
            Deque<String> pending = new ArrayDeque<>(references);
            while (!pending.isEmpty()) {
                String entryName = pending.removeFirst() + ".class";
                ZipEntry entry = entries.get(entryName);
                // Core icons/builders stay in the original material-icons-core dependency.
                if (entry == null || !retained.add(entryName)) continue;
                try (InputStream stream = source.getInputStream(entry)) {
                    pending.addAll(iconReferences(stream.readAllBytes()));
                }
            }
            if (retained.isEmpty()) throw new IOException("No Material icon references found in compiled desktop classes");
            Files.createDirectories(output.getParent());
            Path partial = Files.createTempFile(output.getParent(), "icons-", ".jar");
            try {
                try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(partial))) {
                    zip.setLevel(Deflater.BEST_COMPRESSION);
                    for (var item : entries.entrySet()) {
                        String name = item.getKey();
                        // Keep license/module metadata/resources. No class bytes are changed.
                        if (name.endsWith(".class") && !retained.contains(name)) continue;
                        if (name.matches("META-INF/[^/]+\\.(SF|RSA|DSA|EC)"))
                            throw new IOException("Cannot subset a signed icon archive");
                        ZipEntry entry = new ZipEntry(name);
                        entry.setTime(0);
                        zip.putNextEntry(entry);
                        try (InputStream stream = source.getInputStream(item.getValue())) { stream.transferTo(zip); }
                        zip.closeEntry();
                    }
                    ZipEntry provenance = new ZipEntry("META-INF/SQUI-TV-ICON-SUBSET.txt");
                    provenance.setTime(0);
                    zip.putNextEntry(provenance);
                    String hash;
                    try (InputStream stream = Files.newInputStream(input)) {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        byte[] buffer = new byte[65536]; int count;
                        while ((count = stream.read(buffer)) != -1) digest.update(buffer, 0, count);
                        hash = HexFormat.of().formatHex(digest.digest());
                    }
                    zip.write(("Original library: material-icons-extended-desktop\nSource SHA-256: " + hash +
                        "\nUnmodified classes selected from desktop bytecode:\n" + String.join("\n", retained) + "\n")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
                Files.move(partial, output, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(partial); }
            getLogger().lifecycle("Material extended icons: {} classes, {} -> {} bytes", retained.size(),
                Files.size(input), Files.size(output));
        }
    }

    private static Set<String> iconReferences(byte[] bytes) throws IOException {
        Set<String> result = new HashSet<>();
        try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (data.readInt() != 0xCAFEBABE) throw new IOException("Invalid compiled class");
            data.readUnsignedShort(); data.readUnsignedShort();
            int count = data.readUnsignedShort();
            for (int index = 1; index < count; index++) {
                int tag = data.readUnsignedByte();
                switch (tag) {
                    case 1 -> {
                        // Owners, descriptors and class-name literals in the constant pool.
                        var matcher = ICON.matcher(data.readUTF().replace('.', '/'));
                        while (matcher.find()) result.add(matcher.group());
                    }
                    case 3, 4, 9, 10, 11, 12, 17, 18 -> data.skipNBytes(4);
                    case 5, 6 -> { data.skipNBytes(8); index++; }
                    case 7, 8, 16, 19, 20 -> data.skipNBytes(2);
                    case 15 -> data.skipNBytes(3);
                    default -> throw new IOException("Unsupported class constant tag: " + tag);
                }
            }
        }
        return result;
    }
}
