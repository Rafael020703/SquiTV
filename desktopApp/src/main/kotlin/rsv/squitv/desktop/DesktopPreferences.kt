package rsv.squitv.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*

@Serializable
internal data class DesktopPreferences(val schema: Int = 1, val autoPlayEpisodes: Boolean = true,
    val interfaceScale: Float = 1f, val volume: Int = 80, val muted: Boolean = false,
    val fullscreen: Boolean = true, val ignoredUpdateVersion: String? = null)

/** Variant-local preferences only. Credentials remain exclusively in the existing DPAPI store. */
internal class DesktopPreferencesStore(private val directory: Path, private val scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(DesktopPreferences())
    val state = mutable.asStateFlow()
    private val failure = MutableStateFlow<String?>(null)
    val error = failure.asStateFlow()
    private val ready = MutableStateFlow(false)
    val loaded = ready.asStateFlow()
    private var writable = true
    private val file = directory.resolve("preferences.json")
    init {
        scope.launch {
            mutex.withLock {
                try {
                    val saved = withContext(Dispatchers.IO) {
                        if (Files.exists(file)) {
                            require(Files.size(file) <= 64 * 1024)
                            json.decodeFromString<DesktopPreferences>(Files.readString(file))
                        } else DesktopPreferences()
                    }
                    require(saved.schema == 1 && saved.interfaceScale in .8f..1.25f && saved.volume in 0..100)
                    mutable.value = saved
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    writable = false
                    failure.value = "As preferências salvas não puderam ser lidas. O arquivo foi preservado."
                } finally { ready.value = true }
            }
        }
    }

    fun update(transform: (DesktopPreferences) -> DesktopPreferences) {
        scope.launch { updateAndAwait(transform) }
    }

    suspend fun updateAndAwait(transform: (DesktopPreferences) -> DesktopPreferences) {
        mutex.withLock {
            if (!ready.value || !writable) return@withLock
            val next = transform(mutable.value)
            if (next == mutable.value) return@withLock
            require(next.interfaceScale in .8f..1.25f && next.volume in 0..100)
            try {
                withContext(Dispatchers.IO) {
                    Files.createDirectories(directory)
                    val temp = Files.createTempFile(directory, "preferences-", ".tmp")
                    try {
                        Files.writeString(temp, json.encodeToString(next))
                        try { Files.move(temp, file, ATOMIC_MOVE, REPLACE_EXISTING) }
                        catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temp, file, REPLACE_EXISTING) }
                    } finally { Files.deleteIfExists(temp) }
                }
                mutable.value = next
                failure.value = null
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failure.value = "Não foi possível salvar a configuração. Tente novamente." }
        }
    }
}
