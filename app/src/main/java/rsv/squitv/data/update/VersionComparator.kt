package rsv.squitv.data.update

object VersionComparator {
    fun compareVersions(version1: String, version2: String): Int =
        rsv.squitv.shared.update.VersionComparator.compareVersions(version1, version2)

    fun isUpdateAvailable(currentVersion: String, remoteVersion: String): Boolean =
        rsv.squitv.shared.update.VersionComparator.isUpdateAvailable(currentVersion, remoteVersion)
}
