package rsv.squitv.shared.update

object VersionComparator {
    private val stableVersion = Regex("[vV]?(?:0|[1-9][0-9]*)(?:\\.(?:0|[1-9][0-9]*)){0,3}")

    private fun parts(value: String): List<Int>? {
        val version = value.trim()
        if (version.length > 64 || !stableVersion.matches(version)) return null
        val numbers = version.removePrefix("v").removePrefix("V").split('.')
        return numbers.map { it.toIntOrNull() ?: return null }
    }

    fun isValidVersion(value: String): Boolean = parts(value) != null

    /** Stable numeric versions only; missing trailing components compare as zero. */
    fun compareVersions(version1: String, version2: String): Int {
        val left = requireNotNull(parts(version1)) { "Invalid stable version: $version1" }
        val right = requireNotNull(parts(version2)) { "Invalid stable version: $version2" }
        for (index in 0 until maxOf(left.size, right.size)) {
            val comparison = left.getOrElse(index) { 0 }.compareTo(right.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }

    fun isUpdateAvailable(currentVersion: String, remoteVersion: String): Boolean =
        compareVersions(remoteVersion, currentVersion) > 0
}
