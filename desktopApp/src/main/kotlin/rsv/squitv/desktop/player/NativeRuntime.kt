package rsv.squitv.desktop.player

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import rsv.squitv.desktop.DesktopBuildInfo
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

@Serializable
private data class NativeRuntimeFile(val path: String, val size: Long, val sha256: String)
@Serializable
private data class NativeRuntimeManifest(val id: String, val architecture: String, val files: List<NativeRuntimeFile>)

/** Setup owns component installation. The player only reads/verifies the exact required cache;
 * it never downloads, installs, searches PATH or loads an arbitrary global VLC. */
internal object NativeRuntime {
    private val json = Json { ignoreUnknownKeys = true }

    fun directory(): Path {
        val packaged = System.getProperty("compose.application.resources.dir")?.let { Path.of(it, "vlc") }
        val development = System.getProperty("squitv.vlc.dir")?.let(Path::of)
        listOfNotNull(packaged, development).firstOrNull {
            Files.isRegularFile(it.resolve("libvlc.dll")) && Files.isRegularFile(it.resolve("libvlccore.dll"))
        }?.let { return it }
        val manifestText = NativeRuntime::class.java.getResourceAsStream("/native-runtime/SQUI-TV-RUNTIME.json")?.use {
            it.readAllBytes().toString(Charsets.UTF_8)
        } ?: error("Runtime contract missing")
        val manifest = json.decodeFromString<NativeRuntimeManifest>(manifestText)
        require(manifest.architecture == "x64" && manifest.id.matches(Regex("vlc-[0-9]+\\.[0-9]+\\.[0-9]+-x64-[a-f0-9]{16}")))
        val roots = listOfNotNull(System.getenv("PROGRAMDATA"), System.getenv("LOCALAPPDATA")).filter(String::isNotBlank)
        for (root in roots) {
            val directory = Path.of(root, DesktopBuildInfo.DATA_FOLDER + "Components", manifest.id).toAbsolutePath().normalize()
            if (!Files.isDirectory(directory)) continue
            try { return verifyDirectory(directory, manifestText) } catch (_: Exception) { /* Try only the other pinned cache. */ }
        }
        error("Verified runtime missing; run the installer to repair components")
    }

    internal fun verifyDirectory(directory: Path, manifestText: String): Path {
        val manifest = json.decodeFromString<NativeRuntimeManifest>(manifestText)
        require(manifest.architecture == "x64" && manifest.id.matches(Regex("vlc-[0-9]+\\.[0-9]+\\.[0-9]+-x64-[a-f0-9]{16}")))
        require(manifest.files.isNotEmpty() && manifest.files.size <= 4096)
        require(Files.isDirectory(directory) && !Files.isSymbolicLink(directory))
        val realRoot = directory.toRealPath()
        val expected = manifest.files.map { file ->
            require(file.path.isNotBlank() && !file.path.contains('\\') && !file.path.contains(':') &&
                file.path.split('/').all { it.isNotEmpty() && it != "." && it != ".." } &&
                file.size >= 0 && file.sha256.matches(Regex("[a-f0-9]{64}")))
            file.path
        }.toSet()
        require(expected.size == manifest.files.size && "libvlc.dll" in expected && "libvlccore.dll" in expected)
        Files.walk(directory).use { paths ->
            paths.forEach { path ->
                require(!Files.isSymbolicLink(path) && path.toRealPath().startsWith(realRoot))
                if (Files.isRegularFile(path)) require(directory.relativize(path).toString().replace('\\', '/') in expected)
            }
        }
        for (file in manifest.files) {
            val path = directory.resolve(file.path).normalize()
            require(path.startsWith(directory) && Files.isRegularFile(path) && Files.size(path) == file.size)
            val hash = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(path).use { input ->
                val buffer = ByteArray(65536)
                while (true) { val count = input.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) }
            }
            require(HexFormat.of().formatHex(hash.digest()) == file.sha256)
        }
        return directory
    }
}
