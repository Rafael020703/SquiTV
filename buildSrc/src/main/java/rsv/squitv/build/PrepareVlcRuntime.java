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
import java.util.zip.*;

/** Unmodified official x64 LibVLC runtime, downloaded only during Windows packaging. */
public abstract class PrepareVlcRuntime extends DefaultTask {
    private static final String URL = "https://download.videolan.org/pub/videolan/vlc/3.0.23/win64/vlc-3.0.23-win64.zip";
    private static final String HASH = "992d19dbd0b8a7cde9167d2f7780b1ef6f92acc8a71acfa736101a21f35181e1";
    @OutputDirectory public abstract DirectoryProperty getOutputDirectory();
    @Internal public abstract RegularFileProperty getArchiveFile();
    @TaskAction public void prepare() throws Exception {
        Path archive = getArchiveFile().get().getAsFile().toPath();
        Files.createDirectories(archive.getParent());
        if (!Files.exists(archive)) {
            Path partial = Files.createTempFile(archive.getParent(), "vlc-", ".download");
            try {
                URLConnection connection = URI.create(URL).toURL().openConnection();
                connection.setConnectTimeout(30000); connection.setReadTimeout(60000);
                try (InputStream in = connection.getInputStream()) { Files.copy(in, partial, StandardCopyOption.REPLACE_EXISTING); }
                verify(partial);
                Files.move(partial, archive, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(partial); }
        }
        verify(archive);
        Path root = getOutputDirectory().get().getAsFile().toPath().toAbsolutePath().normalize();
        Path target = root.resolve("windows/vlc");
        Files.createDirectories(target);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.getName().startsWith("vlc-3.0.23/") || entry.isDirectory()) continue;
                String relative = entry.getName().substring("vlc-3.0.23/".length());
                if (!(relative.startsWith("plugins/") || relative.startsWith("lua/") ||
                    relative.startsWith("hrtfs/") || (!relative.contains("/") &&
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
        Files.writeString(target.resolve("SQUI-TV-RUNTIME.txt"), "Unmodified VLC 3.0.23 Windows x64 runtime\nSource: " + URL +
            "\nSHA-256: " + HASH + "\nVideoLAN licenses and authors are included.\n");
    }
    private static void verify(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536]; int count;
            while ((count = in.read(buffer)) >= 0) digest.update(buffer, 0, count);
        }
        if (!HASH.equals(HexFormat.of().formatHex(digest.digest())))
            throw new IOException("VLC runtime SHA-256 mismatch");
    }
}
