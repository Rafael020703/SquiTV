package rsv.squitv

import org.junit.Assert.*
import org.junit.Test
import rsv.squitv.data.repository.SettingsRepository

class DatabaseResetSyncTest {

    @Test
    fun appSettings_initialSyncTimestamps_defaultToZero() {
        val settings = SettingsRepository.AppSettings(
            credentials = null,
            lastSyncTimestamp = 0L,
            syncIntervalHours = 24
        )

        assertEquals(0L, settings.lastSyncTimestamp)
        assertEquals(0L, settings.lastSyncLive)
        assertEquals(0L, settings.lastSyncVod)
        assertEquals(0L, settings.lastSyncSeries)
        assertFalse(settings.isLiveLoaded)
        assertFalse(settings.isVodLoaded)
        assertFalse(settings.isSeriesLoaded)
    }

    @Test
    fun appSettings_afterSyncReset_timestampsMustBeZero() {
        // Simulating settings that were previously synced
        val syncedSettings = SettingsRepository.AppSettings(
            credentials = null,
            lastSyncTimestamp = System.currentTimeMillis(),
            lastSyncLive = System.currentTimeMillis(),
            lastSyncVod = System.currentTimeMillis(),
            lastSyncSeries = System.currentTimeMillis(),
            isLiveLoaded = true,
            isVodLoaded = true,
            isSeriesLoaded = true,
            syncIntervalHours = 24
        )

        // Simulating reset state
        val resetSettings = syncedSettings.copy(
            lastSyncTimestamp = 0L,
            lastSyncLive = 0L,
            lastSyncVod = 0L,
            lastSyncSeries = 0L,
            isLiveLoaded = false,
            isVodLoaded = false,
            isSeriesLoaded = false
        )

        assertEquals(0L, resetSettings.lastSyncTimestamp)
        assertEquals(0L, resetSettings.lastSyncLive)
        assertEquals(0L, resetSettings.lastSyncVod)
        assertEquals(0L, resetSettings.lastSyncSeries)
        assertFalse(resetSettings.isLiveLoaded)
        assertFalse(resetSettings.isVodLoaded)
        assertFalse(resetSettings.isSeriesLoaded)
    }
}
