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
    fun loadDebugDefaults(): ServerCredentials? = null
    fun save(credentials: ServerCredentials)
    fun clear()
    fun allowsAutomaticLogin(): Boolean = false
    fun setAutomaticLogin(enabled: Boolean) {}
}

/** DPAPI is bound to the current Windows user. Plaintext is never written to disk. */
class WindowsCredentialStore(private val directory: Path, private val useDebugDefaults: Boolean = false,
    private val debugDefaultsFile: Path = directory.resolve("debug-login.dpapi")) : CredentialStore {
    private val file = directory.resolve("account.dpapi")
    private val paused = directory.resolve("login.paused")
    @Synchronized
    override fun load(): ServerCredentials? {
        return if (Files.exists(file)) decode(file) else loadDebugDefaults()
    }

    @Synchronized override fun loadDebugDefaults(): ServerCredentials? =
        if (useDebugDefaults && Files.isRegularFile(debugDefaultsFile)) decode(debugDefaultsFile) else null

    private fun decode(source: Path): ServerCredentials {
        require(Files.size(source) <= 64 * 1024) { "Invalid credential file" }
        val clear = Crypt32Util.cryptUnprotectData(Files.readAllBytes(source), WinCrypt.CRYPTPROTECT_UI_FORBIDDEN)
        return try { Json.decodeFromString<ServerCredentials>(clear.toString(Charsets.UTF_8)) }
            finally { clear.fill(0) }
    }

    @Synchronized override fun save(credentials: ServerCredentials) {
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
            Files.deleteIfExists(paused)
        } finally { Files.deleteIfExists(temporary) }
    }

    @Synchronized override fun clear() { Files.deleteIfExists(file); Files.deleteIfExists(paused) }
    @Synchronized override fun allowsAutomaticLogin() = Files.isRegularFile(file) && !Files.exists(paused)
    @Synchronized override fun setAutomaticLogin(enabled: Boolean) {
        if (enabled) Files.deleteIfExists(paused)
        else { Files.createDirectories(directory); Files.writeString(paused, "paused") }
    }
}
