package rsv.squitv.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*

/** Bounded application data. Invalid input is preserved; a failed write never replaces visible state. */
internal class DesktopJsonFile<T>(private val file: Path, initial: T, private val serializer: KSerializer<T>,
    private val maximumBytes: Int, private val label: String, private val validate: (T) -> Unit) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private var read = false
    private var writable = true
    private val mutable = MutableStateFlow(initial)
    val state = mutable.asStateFlow()
    private val failure = MutableStateFlow<String?>(null)
    val error = failure.asStateFlow()

    private suspend fun loadUnlocked() {
        if (read) return
        try {
            val restored = withContext(Dispatchers.IO) {
                if (!Files.exists(file)) null else {
                    require(Files.size(file) in 1..maximumBytes.toLong())
                    json.decodeFromString(serializer, Files.readString(file))
                }
            }
            if (restored != null) { validate(restored); mutable.value = restored }
            read = true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            read = true; writable = false
            failure.value = "Não foi possível ler $label. O arquivo foi preservado."
        }
    }

    suspend fun load(): T = mutex.withLock { loadUnlocked(); mutable.value }

    suspend fun update(transform: (T) -> T): Boolean = mutex.withLock {
        loadUnlocked()
        if (!writable) return@withLock false
        try {
            val next = transform(mutable.value)
            validate(next)
            if (next == mutable.value) return@withLock true
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                withContext(Dispatchers.IO) {
                    val payload = json.encodeToString(serializer, next)
                    require(payload.toByteArray(Charsets.UTF_8).size <= maximumBytes)
                    Files.createDirectories(file.parent)
                    val temp = Files.createTempFile(file.parent, "data-", ".tmp")
                    try {
                        Files.writeString(temp, payload)
                        try { Files.move(temp, file, ATOMIC_MOVE, REPLACE_EXISTING) }
                        catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temp, file, REPLACE_EXISTING) }
                    } finally { Files.deleteIfExists(temp) }
                }
                mutable.value = next
                failure.value = null
            }
            true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { failure.value = "Não foi possível salvar $label. Os dados anteriores foram mantidos."; false }
    }
}
