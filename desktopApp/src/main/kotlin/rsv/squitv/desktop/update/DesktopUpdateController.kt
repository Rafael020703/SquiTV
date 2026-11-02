package rsv.squitv.desktop.update

import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.Shell32
import com.sun.jna.platform.win32.WinUser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.nio.file.Path
import rsv.squitv.desktop.DesktopBuildInfo
import rsv.squitv.desktop.DesktopPreferencesStore

internal enum class UpdatePhase { DISABLED, IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, READY, INSTALLING, ERROR }
internal data class DesktopUpdateState(val phase: UpdatePhase, val info: WindowsUpdate? = null,
    val bytes: Long = 0, val file: Path? = null, val error: String? = null, val prompt: Boolean = false)

internal class DesktopUpdateController(directory: Path, private val preferences: DesktopPreferencesStore,
    private val scope: CoroutineScope) : AutoCloseable {
    private val enabled = !DesktopBuildInfo.DEBUG
    private val repository by lazy { WindowsUpdateRepository(directory) }
    private var initialized = false
    private val mutable = MutableStateFlow(DesktopUpdateState(if (enabled) UpdatePhase.IDLE else UpdatePhase.DISABLED))
    val state = mutable.asStateFlow()
    private var job: Job? = null
    @Volatile private var generation = 0L
    private var checkedAt = 0L
    private var notifiedVersion: String? = null

    fun check(force: Boolean = false, notify: Boolean = false) {
        if (!enabled || job?.isActive == true) return
        if (!force && checkedAt != 0L && System.nanoTime() - checkedAt < 12L * 60 * 60 * 1_000_000_000L) return
        val token = ++generation
        checkedAt = 0L
        mutable.value = DesktopUpdateState(UpdatePhase.CHECKING)
        job = scope.launch {
            try {
                initialized = true
                val architecture = when (System.getProperty("os.arch").lowercase()) {
                    "amd64", "x86_64" -> "x64"
                    "aarch64", "arm64" -> "arm64"
                    else -> error("Unsupported architecture")
                }
                val info = withContext(Dispatchers.IO) { repository.discover(DesktopBuildInfo.VERSION, architecture) }
                ensureActive()
                if (token != generation) return@launch
                checkedAt = System.nanoTime()
                val shouldPrompt = notify && info != null && info.artifact.versionName != notifiedVersion &&
                    info.artifact.versionName != preferences.state.value.ignoredUpdateVersion
                if (info != null) notifiedVersion = info.artifact.versionName
                mutable.value = DesktopUpdateState(if (info == null) UpdatePhase.CURRENT else UpdatePhase.AVAILABLE,
                    info = info, prompt = shouldPrompt)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                checkedAt = 0
                if (token == generation) mutable.value = DesktopUpdateState(UpdatePhase.ERROR,
                    error = "Não foi possível verificar as atualizações. Confira a conexão e tente novamente.")
            }
        }
    }

    fun dismissPrompt(ignore: Boolean = false) {
        if (ignore) mutable.value.info?.artifact?.versionName?.let { version ->
            preferences.update { it.copy(ignoredUpdateVersion = version) }
        }
        mutable.update { it.copy(prompt = false) }
    }

    fun download() {
        if (!enabled || job?.isActive == true) return
        val info = mutable.value.info ?: return
        val token = ++generation
        mutable.value = DesktopUpdateState(UpdatePhase.DOWNLOADING, info)
        job = scope.launch {
            try {
                initialized = true
                val file = repository.download(info) { count ->
                    mutable.update { if (token == generation) it.copy(bytes = count) else it }
                }
                ensureActive()
                kotlin.check(withContext(Dispatchers.IO) { repository.verified(file, info.artifact) })
                ensureActive()
                if (token == generation) mutable.value = DesktopUpdateState(UpdatePhase.READY, info, info.artifact.sizeBytes, file)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                checkedAt = 0
                if (token == generation) mutable.value = DesktopUpdateState(UpdatePhase.ERROR, info,
                    error = "Não foi possível baixar ou validar o instalador. Tente novamente.")
            }
        }
    }

    fun cancel() {
        if (!enabled || mutable.value.phase == UpdatePhase.INSTALLING) return
        ++generation; job?.cancel(); job = null
        val info = mutable.value.info
        mutable.value = DesktopUpdateState(if (info == null) UpdatePhase.IDLE else UpdatePhase.AVAILABLE, info)
    }

    /** Only called by the explicit Install button; never from discovery or a scheduler. */
    fun install(onLaunched: () -> Unit) {
        if (!enabled || job?.isActive == true || mutable.value.phase != UpdatePhase.READY) return
        val ready = mutable.value
        val info = ready.info ?: return
        val file = ready.file ?: return
        mutable.value = ready.copy(phase = UpdatePhase.INSTALLING)
        job = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    kotlin.check(repository.verified(file, info.artifact))
                    val com = Ole32.INSTANCE.CoInitializeEx(null, 2) // STA on this worker for ShellExecute.
                    try {
                        val result = Shell32.INSTANCE.ShellExecute(null, "open", file.toAbsolutePath().toString(), null,
                            file.toAbsolutePath().parent.toString(), WinUser.SW_SHOWNORMAL)
                        kotlin.check(result.toLong() > 32L) { "Installer launch refused" }
                    } finally { if (com.toInt() >= 0) Ole32.INSTANCE.CoUninitialize() }
                }
                onLaunched()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = ready.copy(error = "Não foi possível validar ou abrir o instalador. Tente baixar novamente.") }
        }
    }

    override fun close() { ++generation; job?.cancel(); if (initialized) repository.close() }
}
