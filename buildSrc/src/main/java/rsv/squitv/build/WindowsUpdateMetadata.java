package rsv.squitv.build;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/** Records the actual release installer bytes and architecture of the packaged runtime. */
public abstract class WindowsUpdateMetadata extends DefaultTask {
    @InputFile public abstract RegularFileProperty getInstaller();
    @InputFile public abstract RegularFileProperty getRuntimeJvm();
    @Input public abstract Property<String> getVersion();
    @Input public abstract Property<Boolean> getDebug();
    @OutputFile public abstract RegularFileProperty getMetadataFile();

    @TaskAction public void writeMetadata() throws Exception {
        if (getDebug().get()) throw new IllegalStateException("Debug cannot produce production update metadata");
        String version = getVersion().get();
        if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) throw new IllegalStateException("Invalid Windows version");
        File installer = getInstaller().get().getAsFile();
        if (!installer.getName().equals("SquiTV-" + version + ".exe")) throw new IllegalStateException("Wrong installer identity");
        String architecture;
        try (RandomAccessFile file = new RandomAccessFile(getRuntimeJvm().get().getAsFile(), "r")) {
            if (file.readUnsignedShort() != 0x4d5a) throw new IllegalStateException("Invalid runtime MZ");
            file.seek(0x3c);
            long offset = Integer.toUnsignedLong(Integer.reverseBytes(file.readInt()));
            if (offset < 64 || offset > file.length() - 6) throw new IllegalStateException("Invalid runtime PE offset");
            file.seek(offset);
            if (file.readInt() != 0x50450000) throw new IllegalStateException("Invalid runtime PE");
            int machine = Short.toUnsignedInt(Short.reverseBytes(file.readShort()));
            architecture = switch (machine) {
                case 0x8664 -> "x64";
                case 0xaa64 -> "arm64";
                default -> throw new IllegalStateException("Unsupported runtime architecture");
            };
        }
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(installer.toPath())) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) hash.update(buffer, 0, count);
        }
        String payload = """
            {
              "schemaVersion": 1,
              "platform": "windows",
              "kind": "exe",
              "applicationName": "SquiTV",
              "variant": "release",
              "architecture": "%s",
              "versionName": "%s",
              "assetName": "%s",
              "sizeBytes": %d,
              "sha256": "%s"
            }
            """.formatted(architecture, version, installer.getName(), installer.length(), HexFormat.of().formatHex(hash.digest()));
        File output = getMetadataFile().get().getAsFile();
        Files.createDirectories(output.toPath().getParent());
        Files.writeString(output.toPath(), payload, StandardCharsets.UTF_8);
    }
}
