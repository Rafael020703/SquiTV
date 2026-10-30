package rsv.squitv.data.update

import android.content.Context
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import rsv.squitv.R
import rsv.squitv.di.ApiOkHttpClient
import rsv.squitv.domain.model.AppUpdateInfo
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DownloadStatus {
    data object Idle : DownloadStatus
    data class Downloading(val updateInfo: AppUpdateInfo, val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : DownloadStatus
    data class VerifyingIntegrity(val updateInfo: AppUpdateInfo) : DownloadStatus
    data class Completed(val apkFile: File, val updateInfo: AppUpdateInfo) : DownloadStatus
    data class Failed(val errorMessage: String, val updateInfo: AppUpdateInfo) : DownloadStatus
}

@Singleton
class ApkDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApiOkHttpClient private val okHttpClient: OkHttpClient
) {
    private val _downloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus = _downloadStatus.asStateFlow()
    private val downloadMutex = Mutex()
    @Volatile private var activeJob: Job? = null
    @Volatile private var activeCall: Call? = null

    suspend fun downloadApk(info: AppUpdateInfo) = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
            coroutineScope {
                activeJob = currentCoroutineContext().job
                val directory = File(context.cacheDir, "updates")
                val version = info.versionName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val target = File(directory, "update-$version.apk")
                val temp = File(directory, "update-$version.tmp")
                try {
                    check(directory.isDirectory || directory.mkdirs()) { context.getString(R.string.updater_save_failed) }
                    directory.listFiles()?.filter { it != target && (it.extension == "apk" || it.extension == "tmp") }
                        ?.forEach { it.delete() }
                    if (target.exists()) {
                        _downloadStatus.value = DownloadStatus.VerifyingIntegrity(info)
                        if (validate(target, info) == null) {
                            _downloadStatus.value = DownloadStatus.Completed(target, info)
                            return@coroutineScope
                        }
                        target.delete()
                    }
                    _downloadStatus.value = DownloadStatus.Downloading(info, 0, 0, info.apkSize)
                    val call = okHttpClient.newCall(Request.Builder().url(info.apkUrl).build())
                    activeCall = call
                    // Cancels blocking execute/read as soon as the owning coroutine is cancelled,
                    // including navigation away from a destroyed ViewModel.
                    val cancellationWatcher = launch(start = CoroutineStart.UNDISPATCHED) {
                        try { awaitCancellation() } finally { call.cancel() }
                    }
                    try {
                        call.execute().use { response ->
                            check(response.isSuccessful) { context.getString(R.string.updater_download_failed) }
                            val body = checkNotNull(response.body) { context.getString(R.string.updater_download_failed) }
                            val contentLength = body.contentLength()
                            val total = if (contentLength > 0) contentLength else info.apkSize
                            var downloaded = 0L
                            var lastEmission = 0L
                            body.byteStream().use { input ->
                                temp.outputStream().buffered().use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        output.write(buffer, 0, count)
                                        downloaded += count
                                        val now = SystemClock.elapsedRealtime()
                                        if (now - lastEmission >= 200) {
                                            _downloadStatus.value = DownloadStatus.Downloading(
                                                info, if (total > 0) (downloaded * 100 / total).toInt().coerceIn(0, 100) else 0,
                                                downloaded, total
                                            )
                                            lastEmission = now
                                        }
                                    }
                                }
                            }
                            check(downloaded > 0 && (contentLength <= 0 || downloaded == contentLength) &&
                                (info.apkSize <= 0 || downloaded == info.apkSize)) {
                                context.getString(R.string.updater_incomplete_download)
                            }
                        }
                    } finally {
                        cancellationWatcher.cancel()
                        activeCall = null
                    }
                    currentCoroutineContext().ensureActive()
                    _downloadStatus.value = DownloadStatus.VerifyingIntegrity(info)
                    val error = validate(temp, info)
                    check(error == null) { error.orEmpty() }
                    currentCoroutineContext().ensureActive()
                    check(temp.renameTo(target)) { context.getString(R.string.updater_save_failed) }
                    _downloadStatus.value = DownloadStatus.Completed(target, info)
                } catch (e: CancellationException) {
                    _downloadStatus.value = DownloadStatus.Idle
                    throw e
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    Timber.w(e, "Falha no download da atualização")
                    val message = if (e is IllegalStateException) e.message else null
                    _downloadStatus.value = DownloadStatus.Failed(message ?: context.getString(R.string.updater_download_failed), info)
                } finally {
                    // OkHttp cancellation can surface as IOException, including from a
                    // blocking read. Clear progress even when ensureActive throws in catch.
                    if (!currentCoroutineContext().isActive) _downloadStatus.value = DownloadStatus.Idle
                    temp.delete()
                    activeCall = null
                    activeJob = null
                }
            }
        }
    }

    fun cancelDownload() {
        activeJob?.cancel()
        activeCall?.cancel()
    }

    fun resetState() {
        if (activeJob?.isActive != true) _downloadStatus.value = DownloadStatus.Idle
    }

    private suspend fun validate(file: File, info: AppUpdateInfo): String? {
        currentCoroutineContext().ensureActive()
        if (info.apkSize > 0 && file.length() != info.apkSize) return context.getString(R.string.updater_incomplete_download)
        val expected = info.sha256Digest?.substringAfter(':')?.trim()
        if (!expected.isNullOrBlank()) {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            if (!digest.digest().joinToString("") { "%02x".format(it) }.equals(expected, ignoreCase = true)) {
                return context.getString(R.string.integrity_failed_desc)
            }
        }
        return PackageInstallerHelper.validationError(context, file, info.versionName)
    }
}
