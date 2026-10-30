package rsv.squitv.data.repository

import android.content.Context
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import rsv.squitv.BuildConfig
import rsv.squitv.R
import rsv.squitv.data.update.GitHubApiService
import rsv.squitv.data.update.GitHubUpdateConfig
import rsv.squitv.data.update.VersionComparator
import rsv.squitv.domain.model.AppUpdateInfo
import rsv.squitv.domain.model.UpdateCheckResult
import rsv.squitv.util.AppVersionProvider
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gitHubApiService: GitHubApiService,
    private val settingsRepository: SettingsRepository
) {
    val currentVersionName: String
        get() = AppVersionProvider.getFormattedVersionName(context)
    val currentVersionCode: Long
        get() = AppVersionProvider.getVersionCode(context)

    private val checkMutex = Mutex()
    private var cachedResult: UpdateCheckResult? = null
    private var checkedAt = 0L

    // A cold start checks the server: a persisted version alone cannot restore an APK URL.
    suspend fun checkForUpdates(force: Boolean = false): UpdateCheckResult = checkMutex.withLock {
        if (BuildConfig.DEBUG) return@withLock UpdateCheckResult.DebugBuild(currentVersionName)
        val now = SystemClock.elapsedRealtime()
        cachedResult?.let { cached ->
            if (!force && now - checkedAt < TimeUnit.HOURS.toMillis(12)) return@withLock cached
        }
        try {
            val release = try {
                gitHubApiService.getLatestRelease(repo = GitHubUpdateConfig.PRIMARY_REPO)
            } catch (e: HttpException) {
                // Network errors/rate limits must not silently become "up to date".
                if (e.code() != 404) throw e
                gitHubApiService.getLatestRelease(repo = GitHubUpdateConfig.LEGACY_REPO)
            }
            check(!release.isDraft && !release.isPrerelease) { context.getString(R.string.updater_invalid_release) }
            val remoteVersion = release.tagName.trim().removePrefix("v").removePrefix("V")
            check(remoteVersion.matches(Regex("""\d+(?:\.\d+)*"""))) {
                context.getString(R.string.updater_invalid_release)
            }
            val result = if (!VersionComparator.isUpdateAvailable(currentVersionName, remoteVersion)) {
                UpdateCheckResult.UpToDate(currentVersionName)
            } else {
                val apks = release.assets.filter {
                    it.name.endsWith(".apk", ignoreCase = true) &&
                        !it.name.contains("debug", ignoreCase = true) && it.downloadUrl.startsWith("https://")
                }
                val asset = apks.firstOrNull {
                    it.name.startsWith(GitHubUpdateConfig.EXPECTED_APK_PREFIX, ignoreCase = true)
                } ?: apks.firstOrNull() ?: return@withLock UpdateCheckResult.NoCompatibleApk
                val digest = asset.digest?.substringAfter(':')?.trim()?.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }
                    ?: release.body?.let { Regex("""(?i)SHA-?256\s*:?\s*`?([a-f0-9]{64})""").find(it)?.groupValues?.get(1) }
                UpdateCheckResult.UpdateAvailable(AppUpdateInfo(
                    versionName = remoteVersion,
                    releaseName = release.name?.takeIf { it.isNotBlank() } ?: "Squi TV $remoteVersion",
                    tagName = release.tagName,
                    publishedAt = release.publishedAt.orEmpty(),
                    changelog = release.body.orEmpty(),
                    releaseUrl = release.htmlUrl,
                    apkUrl = asset.downloadUrl,
                    apkName = asset.name,
                    apkSize = asset.size,
                    sha256Digest = digest
                ))
            }
            settingsRepository.updateLastUpdateCheck(
                System.currentTimeMillis(), (result as? UpdateCheckResult.UpdateAvailable)?.updateInfo?.versionName
            )
            cachedResult = result
            checkedAt = SystemClock.elapsedRealtime()
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Falha ao consultar atualização")
            UpdateCheckResult.Error(context.getString(
                if (e is IllegalStateException) R.string.updater_invalid_release else R.string.updater_check_failed
            ))
        }
    }
}
