package rsv.squitv.desktop.data

import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.WinCrypt
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import rsv.squitv.shared.xtream.ServerCredentials

interface CredentialStore {
    fun load(): ServerCredentials?
    fun save(credentials: ServerCredentials)
    fun clear()
}

/** DPAPI is bound to the current Windows user. Plaintext is never written to disk. */
class WindowsCredentialStore(private val directory: Path) : CredentialStore {
    private val file = directory.resolve("account.dpapi")
    override fun load(): ServerCredentials? {
        if (!Files.exists(file)) return null
        require(Files.size(file) <= 64 * 1024) { "Invalid credential file" }
        val clear = Crypt32Util.cryptUnprotectData(Files.readAllBytes(file), WinCrypt.CRYPTPROTECT_UI_FORBIDDEN)
        return try { Json.decodeFromString<ServerCredentials>(clear.toString(Charsets.UTF_8)) }
            finally { clear.fill(0) }
    }

    override fun save(credentials: ServerCredentials) {
        Files.createDirectories(directory)
        val clear = Json.encodeToString(credentials).toByteArray(Charsets.UTF_8)
        val encrypted = try { Crypt32Util.cryptProtectData(clear, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN) }
            finally { clear.fill(0) }
        val temporary = Files.createTempFile(directory, "account-", ".tmp")
        try {
            Files.write(temporary, encrypted)
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { Files.deleteIfExists(temporary) }
    }

    override fun clear() { Files.deleteIfExists(file) }
}
