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
    fun loadPortalSession(credentials: ServerCredentials): RememberedPortalSession? = null
    fun savePortalSession(credentials: ServerCredentials, session: RememberedPortalSession) {}
    fun clear()
    fun allowsAutomaticLogin(): Boolean = false
    fun setAutomaticLogin(enabled: Boolean) {}
    fun savedAccounts(): List<ServerCredentials> = emptyList()
    fun removeAccount(accountId: String) {}
}

class RememberedPortalSession(val displayName: String?, val syncSession: String?)

/** DPAPI is bound to the current Windows user. Plaintext is never written to disk. */
class WindowsCredentialStore(private val directory: Path, private val useDebugDefaults: Boolean = false,
    private val debugDefaultsFile: Path = directory.resolve("debug-login.dpapi")) : CredentialStore {
    private val file = directory.resolve("account.dpapi")
    private val paused = directory.resolve("login.paused")
    private val portalNameFile = directory.resolve("account-display.dpapi")
    private val accountsFile = directory.resolve("accounts.dpapi")
    private fun id(credentials: ServerCredentials) = rsv.squitv.desktop.liveCategoryScope(credentials)
        ?: error("Invalid account identity")

    @Synchronized override fun savedAccounts(): List<ServerCredentials> {
        val accounts = if (Files.isRegularFile(accountsFile)) {
            require(Files.size(accountsFile) <= 1024 * 1024) { "Invalid accounts file" }
            val bytes = Crypt32Util.cryptUnprotectData(Files.readAllBytes(accountsFile), WinCrypt.CRYPTPROTECT_UI_FORBIDDEN)
            try { Json.decodeFromString<List<ServerCredentials>>(bytes.toString(Charsets.UTF_8)) }
            finally { bytes.fill(0) }
        } else emptyList()
        val legacy = if (Files.isRegularFile(file)) listOf(decode(file)) else emptyList()
        return (legacy + accounts).distinctBy(::id).also(::validateAccounts)
    }

    private fun validateAccounts(accounts: List<ServerCredentials>) {
        require(accounts.size <= 32) { "Account limit reached" }
        accounts.forEach {
            require(it.username.isNotBlank() && it.username.length <= 1024 && it.password.length in 1..4096 && it.baseUrl.length <= 2048)
            rsv.squitv.shared.xtream.ServerAddress.normalize(it.baseUrl)
        }
    }

    private fun writeAccounts(accounts: List<ServerCredentials>) {
        validateAccounts(accounts)
        val clear = Json.encodeToString(accounts).toByteArray(Charsets.UTF_8)
        val encrypted = try {
            require(clear.size <= 256 * 1024) { "Account storage limit reached" }
            Crypt32Util.cryptProtectData(clear, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN)
        }
            finally { clear.fill(0) }
        atomicWrite(accountsFile, encrypted)
    }

    private fun atomicWrite(target: Path, data: ByteArray) {
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, "account-", ".tmp")
        try {
            Files.write(temporary, data)
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { Files.deleteIfExists(temporary) }
    }

    @Synchronized override fun removeAccount(accountId: String) {
        val existing = savedAccounts()
        writeAccounts(existing.filterNot { id(it) == accountId })
        if (Files.isRegularFile(file) && id(decode(file)) == accountId) {
            Files.deleteIfExists(file)
            Files.deleteIfExists(portalNameFile)
            Files.createDirectories(directory)
            Files.writeString(paused, "paused")
        }
    }
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
        val accounts = savedAccounts().filterNot { id(it) == id(credentials) } + credentials
        writeAccounts(accounts)
        Files.createDirectories(directory)
        val clear = Json.encodeToString(credentials).toByteArray(Charsets.UTF_8)
        val encrypted = try { Crypt32Util.cryptProtectData(clear, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN) }
            finally { clear.fill(0) }
        atomicWrite(file, encrypted)
        Files.deleteIfExists(paused)
    }

    @Synchronized override fun loadPortalSession(credentials: ServerCredentials): RememberedPortalSession? {
        if (!Files.isRegularFile(portalNameFile)) return null
        require(Files.size(portalNameFile) <= 8192)
        val clear = Crypt32Util.cryptUnprotectData(Files.readAllBytes(portalNameFile), WinCrypt.CRYPTPROTECT_UI_FORBIDDEN)
        return try {
            val data = Json.decodeFromString<Map<String, String>>(clear.toString(Charsets.UTF_8))
            if (data["account"] != id(credentials)) return null
            RememberedPortalSession(
                data["name"]?.takeIf { it.isNotBlank() && it.length <= 255 },
                data["sync"]?.takeIf { it.isNotBlank() && it.length <= 2048 },
            )
        } finally { clear.fill(0) }
    }

    @Synchronized override fun savePortalSession(credentials: ServerCredentials, session: RememberedPortalSession) {
        val name = session.displayName?.takeIf { it.isNotBlank() && it.length <= 255 }
        val sync = session.syncSession?.takeIf { it.isNotBlank() && it.length <= 2048 }
        if (name == null && sync == null) {
            Files.deleteIfExists(portalNameFile)
            return
        }
        val data = buildMap {
            put("account", id(credentials))
            name?.let { put("name", it) }
            sync?.let { put("sync", it) }
        }
        val clear = Json.encodeToString(data).toByteArray(Charsets.UTF_8)
        val encrypted = try { Crypt32Util.cryptProtectData(clear, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN) }
            finally { clear.fill(0) }
        atomicWrite(portalNameFile, encrypted)
    }

    @Synchronized override fun clear() {
        // Preserve previously remembered accounts when a new session is opened without Remember.
        if (Files.isRegularFile(file)) writeAccounts(savedAccounts())
        Files.deleteIfExists(file)
        Files.deleteIfExists(portalNameFile)
        Files.deleteIfExists(paused)
    }
    @Synchronized override fun allowsAutomaticLogin() = Files.isRegularFile(file) && !Files.exists(paused)
    @Synchronized override fun setAutomaticLogin(enabled: Boolean) {
        if (enabled) Files.deleteIfExists(paused)
        else { Files.createDirectories(directory); Files.writeString(paused, "paused") }
    }
}
