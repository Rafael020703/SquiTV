package rsv.squitv.build;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
import java.util.*;
import java.util.zip.*;

/** Unmodified official x64 LibVLC runtime, downloaded only during Windows packaging. */
public abstract class PrepareVlcRuntime extends DefaultTask {
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getDefinitionFile();
    @OutputDirectory public abstract DirectoryProperty getOutputDirectory();
    @Internal public abstract RegularFileProperty getArchiveFile();
    @TaskAction public void prepare() throws Exception {
        Properties definition = new Properties();
        try (InputStream in = Files.newInputStream(getDefinitionFile().get().getAsFile().toPath())) { definition.load(in); }
        String version = definition.getProperty("version"), hash = definition.getProperty("sha256");
        String url = definition.getProperty("url"), prefix = definition.getProperty("archivePrefix");
        String architecture = definition.getProperty("architecture");
        long archiveSize = Long.parseLong(definition.getProperty("archiveSize"));
        if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+") || !hash.matches("[a-f0-9]{64}") ||
            !"x64".equals(architecture) || !url.startsWith("https://download.videolan.org/") ||
            !prefix.equals("vlc-" + version + "/") || archiveSize <= 0)
            throw new IOException("Invalid pinned runtime definition");
        List<String> directories = List.of(definition.getProperty("directories").split(","));
        Path archive = getArchiveFile().get().getAsFile().toPath();
        Files.createDirectories(archive.getParent());
        if (!Files.exists(archive)) {
            Path partial = Files.createTempFile(archive.getParent(), "vlc-", ".download");
            try {
                URLConnection connection = URI.create(url).toURL().openConnection();
                connection.setConnectTimeout(30000); connection.setReadTimeout(60000);
                try (InputStream in = connection.getInputStream()) { Files.copy(in, partial, StandardCopyOption.REPLACE_EXISTING); }
                verify(partial, hash, archiveSize);
                Files.move(partial, archive, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(partial); }
        }
        verify(archive, hash, archiveSize);
        Path root = getOutputDirectory().get().getAsFile().toPath().toAbsolutePath().normalize();
        Path target = root.resolve("windows/vlc");
        Files.createDirectories(target);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.getName().startsWith(prefix) || entry.isDirectory()) continue;
                String relative = entry.getName().substring(prefix.length());
                if (!(directories.stream().anyMatch(relative::startsWith) || (!relative.contains("/") &&
                    (relative.endsWith(".dll") || relative.startsWith("COPYING") ||
                    relative.equals("AUTHORS") || relative.equals("THANKS"))))) continue;
                Path file = target.resolve(relative).normalize();
                if (!file.startsWith(target)) throw new IOException("Invalid runtime archive path");
                Files.createDirectories(file.getParent());
                Files.copy(zip, file, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        if (!Files.exists(target.resolve("libvlc.dll")) || !Files.exists(target.resolve("libvlccore.dll")))
            throw new IOException("Incomplete LibVLC runtime");
        String notice = "Unmodified VLC " + version + " Windows " + architecture + " runtime\nSource: " + url +
            "\nSHA-256: " + hash + "\nVideoLAN licenses and authors are included.\n";
        Files.writeString(target.resolve("SQUI-TV-RUNTIME.txt"), notice);
        String id = "vlc-" + version + "-" + architecture + "-" + hash.substring(0, 16);
        List<String> files = new ArrayList<>();
        // Build the manifest from the verified archive selection, never stale output files.
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            for (var entry : Collections.list(zip.entries())) {
                if (entry.isDirectory() || !entry.getName().startsWith(prefix)) continue;
                String relative = entry.getName().substring(prefix.length());
                if (!(directories.stream().anyMatch(relative::startsWith) || (!relative.contains("/") &&
                    (relative.endsWith(".dll") || relative.startsWith("COPYING") || relative.equals("AUTHORS") || relative.equals("THANKS"))))) continue;
                Path file = target.resolve(relative).normalize();
                if (!file.startsWith(target)) throw new IOException("Invalid runtime archive path");
                files.add(fileJson(relative, file));
            }
        }
        files.add(fileJson("SQUI-TV-RUNTIME.txt", target.resolve("SQUI-TV-RUNTIME.txt")));
        Collections.sort(files);
        String manifest = "{\n\"id\":\"" + id + "\",\"version\":\"" + version + "\",\"architecture\":\"" + architecture +
            "\",\"url\":\"" + url + "\",\"archiveSize\":" + archiveSize + ",\"archiveSha256\":\"" + hash +
            "\",\"archivePrefix\":\"" + prefix + "\",\"files\":[\n" + String.join(",\n", files) + "]}\n";
        Files.writeString(target.resolve("SQUI-TV-RUNTIME.json"), manifest);
    }
    private static String fileJson(String name, Path file) throws Exception {
        if (!name.matches("[A-Za-z0-9_./+ -]+") || name.contains("..")) throw new IOException("Invalid component path");
        return "{\"path\":\"" + name + "\",\"size\":" + Files.size(file) + ",\"sha256\":\"" + digest(file) + "\"}";
    }
    private static String digest(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536]; int count;
            while ((count = in.read(buffer)) >= 0) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static void verify(Path file, String hash, long size) throws Exception {
        if (Files.size(file) != size || !hash.equals(digest(file)))
            throw new IOException("VLC runtime SHA-256 mismatch");
    }
}
