package rsv.squitv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rsv.squitv.data.repository.SettingsRepository

class BackgroundAndPipSettingsTest {

    @Test
    fun appSettings_backgroundAndPip_defaultsArePreserved() {
        val settings = SettingsRepository.AppSettings(
            credentials = null,
            lastSyncTimestamp = 0L,
            syncIntervalHours = 24
        )
        // Background audio default is false
        assertFalse(settings.backgroundPlaybackEnabled)
        // PiP auto-enter default is true
        assertTrue(settings.pipAutoEnterEnabled)
    }

    @Test
    fun appSettings_independentCombinations() {
        // Combination 1: Both OFF
        val bothOff = SettingsRepository.AppSettings(
            credentials = null,
            lastSyncTimestamp = 0L,
            syncIntervalHours = 24,
            backgroundPlaybackEnabled = false,
            pipAutoEnterEnabled = false
        )
        assertFalse(bothOff.backgroundPlaybackEnabled)
        assertFalse(bothOff.pipAutoEnterEnabled)

        // Combination 2: Only Audio ON
        val onlyAudio = SettingsRepository.AppSettings(
            credentials = null,
            lastSyncTimestamp = 0L,
            syncIntervalHours = 24,
            backgroundPlaybackEnabled = true,
            pipAutoEnterEnabled = false
        )
        assertTrue(onlyAudio.backgroundPlaybackEnabled)
        assertFalse(onlyAudio.pipAutoEnterEnabled)

        // Combination 3: Only PiP ON
        val onlyPip = SettingsRepository.AppSettings(
            credentials = null,
            lastSyncTimestamp = 0L,
            syncIntervalHours = 24,
            backgroundPlaybackEnabled = false,
            pipAutoEnterEnabled = true
        )
        assertFalse(onlyPip.backgroundPlaybackEnabled)
        assertTrue(onlyPip.pipAutoEnterEnabled)

        // Combination 4: Both ON
        val bothOn = SettingsRepository.AppSettings(
            credentials = null,
            lastSyncTimestamp = 0L,
            syncIntervalHours = 24,
            backgroundPlaybackEnabled = true,
            pipAutoEnterEnabled = true
        )
        assertTrue(bothOn.backgroundPlaybackEnabled)
        assertTrue(bothOn.pipAutoEnterEnabled)
    }
}
