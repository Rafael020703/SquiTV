package rsv.squitv.build;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import javax.imageio.ImageIO;

public abstract class GenerateDesktopAssets extends DefaultTask {
    @OutputDirectory public abstract DirectoryProperty getOutputDirectory();
    @TaskAction public void generate() throws IOException {
        Path brand = getOutputDirectory().get().getAsFile().toPath().resolve("brand");
        Files.createDirectories(brand);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(BrandIcon.render(256, false), "png", png);
        byte[] bytes = png.toByteArray();
        Files.write(brand.resolve("app-icon.png"), bytes);
        ByteBuffer ico = ByteBuffer.allocate(22 + bytes.length).order(ByteOrder.LITTLE_ENDIAN);
        ico.putShort((short)0).putShort((short)1).putShort((short)1);
        ico.put((byte)0).put((byte)0).put((byte)0).put((byte)0);
        ico.putShort((short)1).putShort((short)32).putInt(bytes.length).putInt(22).put(bytes);
        Files.write(brand.resolve("app-icon.ico"), ico.array());
    }
}
