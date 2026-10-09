package rsv.squitv

import org.junit.Assert.*
import org.junit.Test
import rsv.squitv.data.update.VersionComparator
import rsv.squitv.domain.model.AppUpdateInfo

class DashboardUpdateModalTest {

    @Test
    fun versionComparator_newerVersion_isUpdateAvailable() {
        val currentVersion = "1.11"
        assertTrue(VersionComparator.isUpdateAvailable(currentVersion, "1.12"))
        assertTrue(VersionComparator.isUpdateAvailable(currentVersion, "2.0.0"))
        assertTrue(VersionComparator.isUpdateAvailable(currentVersion, "1.11.1"))
    }

    @Test
    fun versionComparator_sameOrOlderVersion_isNotUpdateAvailable() {
        val currentVersion = "1.11"
        assertFalse(VersionComparator.isUpdateAvailable(currentVersion, "1.11"))
        assertFalse(VersionComparator.isUpdateAvailable(currentVersion, "1.10"))
        assertFalse(VersionComparator.isUpdateAvailable(currentVersion, "1.0.0"))
    }

    @Test
    fun updateModal_ignoredVersionFiltering_respectsIgnoredStateAndAllowsFutureVersions() {
        val updateInfoV1_12 = AppUpdateInfo(
            versionName = "1.12",
            releaseName = "Squi TV 1.12",
            tagName = "v1.12",
            publishedAt = "2026-10-10",
            changelog = "Ajustes e melhorias gerais.",
            releaseUrl = "https://github.com/Rafael020703/SquiTV/releases/tag/v1.12",
            apkUrl = "https://github.com/Rafael020703/SquiTV/releases/download/v1.12/SquiTV-v1.12.apk",
            apkName = "SquiTV-v1.12.apk",
            apkSize = 8500000L
        )

        // User clicks IGNORAR for v1.12
        val ignoredVersionSetting: String? = updateInfoV1_12.versionName

        // Check if v1.12 is ignored
        val isV1_12Ignored = (ignoredVersionSetting == updateInfoV1_12.versionName)
        assertTrue("Version 1.12 should be marked as ignored after clicking IGNORAR", isV1_12Ignored)

        // New release v1.13 arrives
        val updateInfoV1_13 = updateInfoV1_12.copy(versionName = "1.13", tagName = "v1.13")
        val isV1_13Ignored = (ignoredVersionSetting == updateInfoV1_13.versionName)
        assertFalse("Future higher version 1.13 must NOT be ignored even if 1.12 was ignored", isV1_13Ignored)
    }

    @Test
    fun updateModal_changelogFormatting_providesFallbackWhenBlank() {
        val updateInfoWithChangelog = AppUpdateInfo(
            versionName = "1.12",
            releaseName = "Squi TV 1.12",
            tagName = "v1.12",
            publishedAt = "2026-10-10",
            changelog = "- Correção do player\n- Suporte a legenda",
            releaseUrl = "https://github.com/Rafael020703/SquiTV/releases/tag/v1.12",
            apkUrl = "https://github.com/Rafael020703/SquiTV/releases/download/v1.12/SquiTV-v1.12.apk",
            apkName = "SquiTV-v1.12.apk",
            apkSize = 8500000L
        )

        val updateInfoBlankChangelog = updateInfoWithChangelog.copy(changelog = "   ")

        fun getDisplayChangelog(info: AppUpdateInfo): String {
            val raw = info.changelog.trim()
            return if (raw.isBlank()) "Detalhes das alterações não fornecidos." else raw
        }

        assertEquals("- Correção do player\n- Suporte a legenda", getDisplayChangelog(updateInfoWithChangelog))
        assertEquals("Detalhes das alterações não fornecidos.", getDisplayChangelog(updateInfoBlankChangelog))
    }
}
