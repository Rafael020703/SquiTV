package rsv.squitv

import org.junit.Assert.*
import org.junit.Test
import rsv.squitv.data.local.entities.FavoriteEntity
import rsv.squitv.data.model.*
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.domain.model.UserProfile

class SettingsBackupCodecTest {
    private val credentials = XtreamCredentials("alice", "do-not-export-this-password", "https://example.test:8080")
    private val key = SettingsBackupCodec.accountKey(credentials)
    private val accountId = SettingsBackupCodec.fingerprint(key)
    private fun backup(): SettingsBackup {
        val settings = SettingsRepository.AppSettings(
            credentials = credentials, accounts = listOf(credentials), lastSyncTimestamp = 0L, syncIntervalHours = 24,
            categoryOrderMap = mapOf("${key}_vod" to listOf("80", "2", "1")),
            hiddenCategoriesMap = mapOf("${key}_vod" to setOf("3")),
            displayNamesMap = mapOf(key to "Minha conta"), hiddenSections = setOf("RECENTS"),
            profiles = listOf(UserProfile("default", "Perfil")))
        return SettingsBackupCodec.create(settings).copy(favorites = listOf(FavoriteEntity(
            streamId = 101, streamType = "MOVIE", name = "Ação", timestamp = 100L, accountId = accountId, profileId = "default")))
    }
    @Test fun completeBackup_roundTripsAndOffersAllActiveOptions() {
        val backup = backup()
        assertEquals(backup, SettingsBackupCodec.decode(SettingsBackupCodec.encode(backup)))
        assertEquals(BackupOption.entries.toSet(), SettingsBackupCodec.available(backup))
        assertEquals(listOf("80", "2", "1"), backup.accounts.single().categories["vod"]!!.order)
    }
    @Test fun loginPasswordAndPin_areExcluded() {
        val text = SettingsBackupCodec.encode(backup())
        assertFalse(text.contains("do-not-export-this-password"))
        // PIN digits can coincidentally occur in timestamps or account hashes.
        assertFalse(text.contains("\"password\""))
        assertFalse(text.contains("\"pin\""))
    }
    @Test fun selectingOnlyOrder_excludesOtherSettingsAndFavorites() {
        val selected = SettingsBackupCodec.select(backup(), setOf(BackupOption.CATEGORY_ORDER))
        assertEquals(setOf(BackupOption.CATEGORY_ORDER), SettingsBackupCodec.available(selected))
        assertNull(selected.favorites)
        assertNull(selected.profiles)
        assertNull(selected.accounts.single().displayName)
        assertNull(selected.accounts.single().categories["vod"]!!.hidden)
    }
    @Test fun favoritesOnly_keepAccountAssociationWithoutImportingProfiles() {
        val selected = SettingsBackupCodec.select(backup(), setOf(BackupOption.FAVORITES))
        assertEquals(setOf(BackupOption.FAVORITES), SettingsBackupCodec.available(selected))
        assertEquals(accountId, selected.favorites!!.single().accountId)
        assertEquals(accountId, selected.accounts.single().fingerprint)
        assertTrue(selected.accounts.single().categories.isEmpty())
        assertNull(selected.profiles)
        assertNull(selected.activeProfileId)
    }
    @Test fun receiverSelection_intersectsWithItemsActuallySent() {
        val sent = SettingsBackupCodec.select(backup(), setOf(BackupOption.FAVORITES, BackupOption.CATEGORY_ORDER))
        val allowed = setOf(BackupOption.FAVORITES, BackupOption.CHANNEL_QUALITY)
        val received = SettingsBackupCodec.select(sent, SettingsBackupCodec.available(sent).intersect(allowed))
        assertEquals(setOf(BackupOption.FAVORITES), SettingsBackupCodec.available(received))
    }
    @Test fun falseAndEmptyValues_remainAvailableForRestoringDefaults() {
        val backup = backup().copy(preferences = BackupPreferences(autoPlayEnabled = false), favorites = emptyList(),
            accounts = listOf(BackupAccount(accountId, mapOf("vod" to BackupCategories(order = emptyList(), hidden = emptySet())))))
        val selected = SettingsBackupCodec.select(backup, setOf(BackupOption.AUTO_PLAY, BackupOption.CATEGORY_ORDER,
            BackupOption.HIDDEN_CATEGORIES, BackupOption.FAVORITES))
        assertEquals(false, selected.preferences.autoPlayEnabled)
        assertEquals(4, SettingsBackupCodec.available(selected).size)
        assertEquals(emptyList<FavoriteEntity>(), SettingsBackupCodec.decode(SettingsBackupCodec.encode(selected)).favorites)
    }
    @Test fun version1Backup_isStillAccepted() {
        val legacy = backup().copy(schemaVersion = 1, profiles = null, activeProfileId = null, profileImageUrl = null, favorites = null)
        assertEquals(legacy, SettingsBackupCodec.decode(SettingsBackupCodec.encode(legacy)))
    }
    @Test fun retiredFieldsInSchemasOneAndTwoAreIgnoredAndNeverReexported() {
        for (schema in listOf(1, 2)) {
            val text = """{"format":"SquiTV.Settings","schemaVersion":$schema,"createdAt":1,
                "preferences":{"playerEngine":"VLC","bufferStrategy":"Fast","useOledTheme":false,
                    "dataSaverMode":true,"defaultResizeMode":4,"showDiagnostics":true,"compactMode":false,
                    "hideBlockedCategories":true,"appPin":"1234","appLockEnabled":true,
                    "detailedNotifications":false,"autoPlayEnabled":false},
                "profiles":[{"id":"default","name":"Perfil","pin":"9999"}],
                "hiddenSections":["EPG","MOVIES"]}"""
            val decoded = SettingsBackupCodec.decode(text)
            assertEquals(false, decoded.preferences.autoPlayEnabled)
            assertNull(decoded.preferences.groupLiveChannels)
            val encoded = SettingsBackupCodec.encode(decoded)
            listOf("playerEngine", "bufferStrategy", "useOledTheme", "dataSaverMode", "defaultResizeMode",
                "showDiagnostics", "compactMode", "hideBlockedCategories", "appPin", "appLockEnabled", "detailedNotifications", "pin")
                .forEach { key -> assertFalse(encoded.contains('"' + key + '"')) }
        }
    }
    @Test fun channelPreferencesRoundTripAndSelectionPreservesFalse() {
        val changed = backup().copy(preferences = BackupPreferences(groupLiveChannels = false, preferredChannelQuality = "HD"))
        val selected = SettingsBackupCodec.select(changed, setOf(BackupOption.GROUP_CHANNELS, BackupOption.CHANNEL_QUALITY))
        val decoded = SettingsBackupCodec.decode(SettingsBackupCodec.encode(selected))
        assertEquals(false, decoded.preferences.groupLiveChannels)
        assertEquals("HD", decoded.preferences.preferredChannelQuality)
        assertEquals(setOf(BackupOption.GROUP_CHANNELS, BackupOption.CHANNEL_QUALITY), SettingsBackupCodec.available(decoded))
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBackupCodec.validate(changed.copy(preferences = BackupPreferences(preferredChannelQuality = "8K")))
        }
    }
    @Test fun emptySelection_isRejected() {
        assertThrows(IllegalArgumentException::class.java) { SettingsBackupCodec.select(backup(), emptySet()) }
    }
    @Test fun malformedAndOversizedFiles_areRejected() {
        assertThrows(Exception::class.java) { SettingsBackupCodec.decode("{") }
        assertThrows(IllegalArgumentException::class.java) { SettingsBackupCodec.decode("x".repeat(SettingsBackupCodec.MAX_BYTES + 1)) }
    }
    @Test fun unknownFormatAndFutureSchema_areRejected() {
        assertThrows(IllegalArgumentException::class.java) { SettingsBackupCodec.validate(backup().copy(format = "OtherApp")) }
        assertThrows(IllegalArgumentException::class.java) { SettingsBackupCodec.validate(backup().copy(schemaVersion = 4)) }
    }
    @Test fun duplicateFavoritesAndMissingAccount_areRejected() {
        val backup = backup()
        assertThrows(IllegalArgumentException::class.java) { SettingsBackupCodec.validate(backup.copy(favorites = backup.favorites.orEmpty() + backup.favorites.orEmpty())) }
        assertThrows(IllegalArgumentException::class.java) { SettingsBackupCodec.validate(backup.copy(accounts = emptyList())) }
    }
    @Test fun duplicateCategoryOrderAndInvalidPreferences_areRejected() {
        val backup = backup()
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBackupCodec.validate(backup.copy(accounts = listOf(BackupAccount(accountId,
                mapOf("vod" to BackupCategories(order = listOf("1", "1")))))))
        }
        assertThrows(IllegalArgumentException::class.java) { SettingsBackupCodec.validate(backup.copy(preferences = BackupPreferences(uiZoom = Float.NaN))) }
        assertThrows(IllegalArgumentException::class.java) { SettingsBackupCodec.validate(backup.copy(preferences = BackupPreferences(language = "invalid"))) }
    }
}
