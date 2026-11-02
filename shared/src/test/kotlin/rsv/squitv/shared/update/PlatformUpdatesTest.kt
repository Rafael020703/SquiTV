package rsv.squitv.shared.update

import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PlatformUpdatesTest {
    private val android = UpdateArtifact("android", "apk", "any", "SquiTV-v1.22.apk", "1.22", 23, 24,
        100, "a".repeat(64), "rsv.squitv")
    private val windows = UpdateArtifact("windows", "exe", "x64", "SquiTV-1.22.0.exe", "1.22.0",
        sizeBytes = 200, sha256 = "b".repeat(64))
    private fun manifest() = UpdateManifest(releaseTag = "v1.22", artifacts = listOf(android, windows),
        changelogs = mapOf("android" to "Android notes", "windows" to "Windows notes"))

    @Test fun notesAreSelectedByPlatformAndLegacyMixedBodyIsHidden() {
        val manifest = manifest()
        assertEquals("Android notes", PlatformUpdateNotes.select(manifest, "android", "mixed"))
        assertEquals("Windows notes", PlatformUpdateNotes.select(manifest, "windows", "mixed"))
        assertEquals("", PlatformUpdateNotes.select(manifest.copy(changelogs = mapOf("windows" to "Windows notes")), "android", "mixed"))
        assertEquals("", PlatformUpdateNotes.select(manifest.copy(changelogs = emptyMap()), "android", "mixed"))
        assertEquals("old notes", PlatformUpdateNotes.select(manifest.copy(artifacts = listOf(android), changelogs = emptyMap()), "android", "old notes"))
    }

    @Test fun unknownAbsentPlatformsAndOversizedNotesAreRejected() {
        assertFalse(UpdateManifestValidator.validate(manifest().copy(changelogs = mapOf("linux" to "wrong"))))
        assertFalse(UpdateManifestValidator.validate(manifest().copy(artifacts = listOf(android))))
        assertFalse(UpdateManifestValidator.validate(manifest().copy(changelogs = mapOf("android" to "x".repeat(20_001)))))
    }

    @Test fun stableReferenceBindsPlatformVersionTagAndManifestBytes() {
        val manifest = manifest()
        val text = Json.encodeToString(manifest)
        val hash = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        val reference = UpdateChannelReference(platform = "android", versionName = "1.22", releaseTag = "v1.22", manifestSha256 = hash)
        assertTrue(reference.validate("android"))
        assertFalse(reference.validate("windows"))
        assertTrue(reference.matches(text, manifest))
        assertFalse(reference.matches(text + "\n", manifest))
        assertFalse(reference.copy(versionName = "1.21").matches(text, manifest))
        assertFalse(reference.copy(releaseTag = "v1.23").matches(text, manifest))
        assertFalse(reference.copy(manifestSha256 = "z".repeat(64)).validate("android"))
    }

    @Test fun windowsPatchTagDoesNotChangeAppVersionOrOfferAndroid() {
        val patch = windows.copy(assetName = "SquiTV-1.22.1.exe", versionName = "1.22.1")
        val manifest = UpdateManifest(releaseTag = "v1.22.1.1", artifacts = listOf(patch), changelogs = mapOf("windows" to "fix"))
        assertTrue(UpdateManifestValidator.validate(manifest))
        assertEquals(patch, UpdateManifestValidator.selectWindowsArtifact(manifest, "1.22.0", "x64"))
        assertNull(UpdateManifestValidator.selectWindowsArtifact(manifest, "1.22.1", "x64"))
        assertNull(UpdateManifestValidator.selectWindowsArtifact(manifest, "1.22.0", "arm64"))
        assertNull(UpdateManifestValidator.selectAndroidArtifact(manifest, "1.22", 23, 37, listOf("arm64-v8a")))
    }
}
