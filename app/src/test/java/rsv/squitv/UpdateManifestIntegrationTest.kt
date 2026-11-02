package rsv.squitv

import org.junit.Assert.*
import org.junit.Test
import rsv.squitv.shared.update.*

class UpdateManifestIntegrationTest {
    @Test fun testSelectAndroidArtifactFromJointManifest() {
        val android = UpdateArtifact("android", "apk", "universal", "SquiTV-v1.21.apk", "1.21",
            22L, 24, 100L, "a".repeat(64), "rsv.squitv", listOf("arm64-v8a", "armeabi-v7a"))
        val windows = UpdateArtifact("windows", "exe", "x64", "SquiTV-v1.21-setup.exe", "1.21",
            sizeBytes = 200L, sha256 = "b".repeat(64))
        val manifest = UpdateManifest(releaseTag = "v1.21", artifacts = listOf(windows, android))
        assertEquals(android, UpdateManifestValidator.selectAndroidArtifact(manifest, "1.20", 21L, 28,
            listOf("arm64-v8a"), "rsv.squitv"))
    }

    @Test fun testInvalidManifestRejected() {
        val manifest = UpdateManifest(schemaVersion = 2, releaseTag = "v1.21")
        assertFalse(UpdateManifestValidator.validate(manifest))
        assertNull(UpdateManifestValidator.selectAndroidArtifact(manifest, "1.20", 21L, 28,
            listOf("arm64-v8a"), "rsv.squitv"))
    }
}
