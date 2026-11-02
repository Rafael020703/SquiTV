package rsv.squitv.shared.update

import java.security.MessageDigest
import kotlinx.serialization.Serializable

/** Published only after the referenced release and all its assets are available. No arbitrary URLs. */
@Serializable
data class UpdateChannelReference(
    val schemaVersion: Int = 1,
    val productId: String = "squi-tv",
    val channel: String = "stable",
    val platform: String,
    val versionName: String,
    val releaseTag: String,
    val manifestSha256: String
) {
    fun validate(expectedPlatform: String): Boolean = schemaVersion == 1 && productId == "squi-tv" &&
        channel == "stable" && platform == expectedPlatform && platform in setOf("android", "windows") &&
        VersionComparator.isValidVersion(versionName) && VersionComparator.isValidVersion(releaseTag) &&
        VersionComparator.compareVersions(versionName, releaseTag) <= 0 && manifestSha256.matches(Regex("[a-fA-F0-9]{64}"))

    fun matches(manifestText: String, manifest: UpdateManifest): Boolean = validate(platform) &&
        UpdateManifestValidator.validate(manifest) && releaseTag == manifest.releaseTag &&
        manifest.artifacts.any { it.platform == platform && it.versionName == versionName } &&
        manifestSha256.equals(MessageDigest.getInstance("SHA-256").digest(manifestText.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }, ignoreCase = true)
}

object PlatformUpdateNotes {
    /** Old single-platform releases retain their notes. Never show another platform's notes. */
    fun select(manifest: UpdateManifest, platform: String, legacyBody: String): String {
        if (manifest.changelogs.isNotEmpty()) return manifest.changelogs[platform].orEmpty()
        return if (manifest.artifacts.map { it.platform }.distinct() == listOf(platform)) legacyBody else ""
    }
}
