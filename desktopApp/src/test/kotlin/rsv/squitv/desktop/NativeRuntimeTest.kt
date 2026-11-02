package rsv.squitv.desktop

import org.junit.Assert.*
import org.junit.Test
import rsv.squitv.desktop.player.NativeRuntime
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

class NativeRuntimeTest {
    private fun hash(bytes: ByteArray) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun manifest(path: String = "libvlc.dll", architecture: String = "x64"): String {
        val files = listOf(path, "libvlccore.dll").joinToString(",") {
            """{"path":"$it","size":3,"sha256":"${hash(byteArrayOf(1, 2, 3))}"}"""
        }
        return """{"id":"vlc-3.0.23-x64-992d19dbd0b8a7cd","architecture":"$architecture","files":[$files]}"""
    }
    private fun fixture(action: (Path) -> Unit) {
        val root = Files.createTempDirectory("squitv-debug-native-check-")
        try {
            Files.write(root.resolve("libvlc.dll"), byteArrayOf(1, 2, 3))
            Files.write(root.resolve("libvlccore.dll"), byteArrayOf(1, 2, 3))
            action(root)
        } finally { Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }
    private fun rejected(action: () -> Unit) {
        try { action(); fail("Invalid runtime accepted") } catch (_: IllegalArgumentException) { }
    }
    @Test fun validManifestMatchesAllFiles() = fixture { root ->
        assertEquals(root, NativeRuntime.verifyDirectory(root, manifest()))
    }
    @Test fun sameLengthCorruptionIsRejected() = fixture { root ->
        Files.write(root.resolve("libvlc.dll"), byteArrayOf(3, 2, 1))
        rejected { NativeRuntime.verifyDirectory(root, manifest()) }
    }
    @Test fun extraDllIsRejected() = fixture { root ->
        Files.writeString(root.resolve("unexpected.dll"), "fixture")
        rejected { NativeRuntime.verifyDirectory(root, manifest()) }
    }
    @Test fun missingDllIsRejected() = fixture { root ->
        Files.delete(root.resolve("libvlc.dll"))
        rejected { NativeRuntime.verifyDirectory(root, manifest()) }
    }
    @Test fun traversalIsRejected() = fixture { root ->
        rejected { NativeRuntime.verifyDirectory(root, manifest("../libvlc.dll")) }
    }
    @Test fun wrongArchitectureIsRejected() = fixture { root ->
        rejected { NativeRuntime.verifyDirectory(root, manifest(architecture = "arm64")) }
    }
}
