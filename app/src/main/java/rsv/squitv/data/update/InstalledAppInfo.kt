package rsv.squitv.data.update

/** Installed application and device metadata, supplied by Android or a controlled test fixture. */
data class InstalledAppInfo(
    val versionName: String,
    val versionCode: Long,
    val applicationId: String,
    val isDebug: Boolean,
    val sdkInt: Int,
    val supportedAbis: List<String>
)
