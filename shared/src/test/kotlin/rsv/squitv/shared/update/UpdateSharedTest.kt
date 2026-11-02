package rsv.squitv.shared.update

import org.junit.Assert.*
import org.junit.Test

class UpdateSharedTest {
    private fun artifact(arch: String = "universal") = UpdateArtifact(
        platform = "android", kind = "apk", architecture = arch, assetName = "SquiTV-v1.21.apk",
        versionName = "1.21", versionCode = 22L, minSdk = 24, sizeBytes = 100L, sha256 = "a".repeat(64),
        applicationId = "rsv.squitv",
        abis = if (arch == "universal") listOf("arm64-v8a", "armeabi-v7a") else listOf(arch)
    )
    private fun manifest(artifact: UpdateArtifact = artifact()) =
        UpdateManifest(releaseTag = "v1.21", artifacts = listOf(artifact))
    private fun select(artifact: UpdateArtifact = artifact(), abis: List<String> = listOf("arm64-v8a"),
                       sdk: Int = 28, code: Long = 21L, version: String = "1.20") =
        UpdateManifestValidator.selectAndroidArtifact(manifest(artifact), version, code, sdk, abis, "rsv.squitv")

    @Test fun numericVersionsAndTrailingZeros() {
        assertTrue(VersionComparator.isUpdateAvailable("1.9", "1.10"))
        assertTrue(VersionComparator.isUpdateAvailable("v1.19.0", "1.20.1"))
        assertEquals(0, VersionComparator.compareVersions("1.20", "V1.20.0"))
        assertFalse(VersionComparator.isUpdateAvailable("1.21", "1.20"))
    }
    @Test fun malformedAndOverflowVersionsAreRejected() {
        for (value in listOf("", "1..2", "1.02", "1.2-beta", "1.2oops", "1.2.", "-1", "1.2147483648", "1.2.3.4.5")) {
            assertFalse(value, VersionComparator.isValidVersion(value))
            assertThrows(IllegalArgumentException::class.java) { VersionComparator.compareVersions(value, "1.20") }
        }
    }
    @Test fun validManifestSelectsAndroid() { assertNotNull(select()) }
    @Test fun packageIsRequiredAndExact() {
        assertNull(select(artifact().copy(applicationId = null)))
        assertNull(select(artifact().copy(applicationId = "com.other.app")))
        assertNull(select(artifact().copy(applicationId = "RSV.SQUITV")))
    }
    @Test fun explicitAbiSupportIsRequired() {
        for ((device, apk) in listOf(
            listOf("arm64-v8a") to "armeabi-v7a", listOf("arm64-v8a") to "x86",
            listOf("x86_64") to "x86", listOf("x86_64") to "armeabi-v7a",
            listOf("armeabi-v7a") to "arm64-v8a", emptyList<String>() to "arm64-v8a"
        )) assertNull(device.toString() + " / " + apk, select(artifact(apk), device))
    }
    @Test fun mixedDeviceAcceptsBothDeclaredAbis() {
        for (abi in listOf("arm64-v8a", "armeabi-v7a")) assertNotNull(select(artifact(abi), listOf("arm64-v8a", "armeabi-v7a")))
    }
    @Test fun universalMeansDeclaredApkAbisNotAllDevices() {
        assertNull(select(abis = listOf("x86_64")))
        assertNull(select(artifact().copy(abis = emptyList())))
        assertNull(select(artifact().copy(abis = listOf("arm64-v8a"))))
    }
    @Test fun anyAbiIsOnlyForApksWithoutNativeLibraries() {
        assertNotNull(select(artifact("any").copy(abis = emptyList()), listOf("x86")))
        assertNull(select(artifact("any").copy(abis = listOf("arm64-v8a"))))
    }
    @Test fun sdkAndCodeAreStrict() {
        assertNull(select(sdk = 23))
        assertNull(select(artifact().copy(versionCode = null)))
        assertNull(select(artifact().copy(versionCode = 21L)))
        assertNull(select(artifact().copy(versionCode = 20L)))
        assertNull(select(artifact().copy(versionName = "1.19")))
        assertNotNull(select(artifact().copy(versionName = "1.20")))
    }
    @Test fun invalidContractFieldsAreRejected() {
        for (bad in listOf(
            artifact().copy(sha256 = "z".repeat(64)), artifact().copy(sizeBytes = -1L),
            artifact().copy(assetName = "../bad.apk"), artifact().copy(assetName = "debug.apk"),
            artifact().copy(versionName = "1.21-beta"), artifact().copy(minSdk = null),
            artifact().copy(versionCode = 2_100_000_001L), artifact("invalid"),
            artifact().copy(abis = listOf("arm64-v8a", "arm64-v8a"))
        )) assertFalse(bad.toString(), UpdateManifestValidator.validate(manifest(bad)))
        assertFalse(UpdateManifestValidator.validate(manifest().copy(channel = "beta")))
        assertFalse(UpdateManifestValidator.validate(manifest().copy(schemaVersion = 2)))
        assertFalse(UpdateManifestValidator.validate(manifest().copy(artifacts = listOf(artifact(), artifact()))))
    }
    @Test fun artifactVersionCannotExceedProductRelease() {
        assertFalse(UpdateManifestValidator.validate(manifest(artifact().copy(versionName = "1.22"))))
    }
    @Test fun windowsAndAndroidCanShareRelease() {
        val windows = UpdateArtifact("windows", "exe", "x64", "SquiTV-v1.21-windows-x64.exe", "1.21",
            sizeBytes = 200L, sha256 = "b".repeat(64))
        val joint = manifest().copy(artifacts = listOf(windows, artifact()))
        assertTrue(UpdateManifestValidator.validate(joint))
        assertEquals("android", UpdateManifestValidator.selectAndroidArtifact(joint, "1.20", 21L, 28,
            listOf("arm64-v8a"), "rsv.squitv")?.platform)
    }
}
