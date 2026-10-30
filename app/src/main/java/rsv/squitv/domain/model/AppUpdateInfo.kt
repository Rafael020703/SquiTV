package rsv.squitv.domain.model

data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Long? = null,
    val releaseName: String,
    val tagName: String,
    val publishedAt: String,
    val changelog: String,
    val releaseUrl: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
    val sha256Digest: String? = null
)

sealed interface UpdateCheckResult {
    data class DebugBuild(val currentVersion: String) : UpdateCheckResult
    data class UpdateAvailable(val updateInfo: AppUpdateInfo) : UpdateCheckResult
    data class UpToDate(val currentVersion: String) : UpdateCheckResult
    data object NoCompatibleApk : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}
