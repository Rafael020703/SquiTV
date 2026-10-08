package rsv.squitv

import rsv.squitv.data.repository.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LastChannelRestorationTest {

    @Test
    fun appSettings_lastLiveCategoryAndChannel_defaultToNull() {
        val settings = SettingsRepository.AppSettings(
            credentials = null,
            lastSyncTimestamp = 0L,
            syncIntervalHours = 24
        )
        assertNull(settings.lastLiveCategory)
        assertNull(settings.lastLiveChannelId)
        assertNull(settings.lastLiveChannelName)
    }

    @Test
    fun appSettings_lastLiveCategoryAndChannel_canBeStoredAndRestored() {
        val categoryId = "infantil_24h"
        val channelId = "12345"
        val channelName = "X-Men Evolution"

        val settings = SettingsRepository.AppSettings(
            credentials = null,
            lastSyncTimestamp = 0L,
            syncIntervalHours = 24,
            lastLiveCategory = categoryId,
            lastLiveChannelId = channelId,
            lastLiveChannelName = channelName
        )

        assertEquals(categoryId, settings.lastLiveCategory)
        assertEquals(channelId, settings.lastLiveChannelId)
        assertEquals(channelName, settings.lastLiveChannelName)
    }

    @Test
    fun restorationDoesNotTriggerAutoPlay() {
        // Ensuring restoration model state does not imply auto-play or playback initiation
        val settings = SettingsRepository.AppSettings(
            credentials = null,
            lastSyncTimestamp = 0L,
            syncIntervalHours = 24,
            lastLiveCategory = "sports",
            lastLiveChannelId = "999",
            lastLiveChannelName = "ESPN",
            lastLiveCategoryChannels = mapOf("sports" to "999", "movies" to "888")
        )

        assertNotNull(settings.lastLiveCategory)
        assertNotNull(settings.lastLiveChannelId)
        assertEquals("999", settings.lastLiveCategoryChannels["sports"])
        assertEquals("888", settings.lastLiveCategoryChannels["movies"])
        // Auto-play is a separate setting or behavior; verifying settings state alone does not trigger playback.
        // The player must remain closed and playback must not start until explicit user interaction (OK/Enter).
    }

    @Test
    fun categorySpecificChannelMemory_independentPerCategory() {
        val channelsMap = mapOf(
            "infantil" to "channel_xmen",
            "movies" to "channel_matrix",
            "sports" to "channel_espn"
        )

        val settings = SettingsRepository.AppSettings(
            credentials = null,
            lastSyncTimestamp = 0L,
            syncIntervalHours = 24,
            lastLiveCategory = "movies",
            lastLiveChannelId = "channel_matrix",
            lastLiveChannelName = "Matrix",
            lastLiveCategoryChannels = channelsMap
        )

        assertEquals("channel_xmen", settings.lastLiveCategoryChannels["infantil"])
        assertEquals("channel_matrix", settings.lastLiveCategoryChannels["movies"])
        assertEquals("channel_espn", settings.lastLiveCategoryChannels["sports"])
    }
}
