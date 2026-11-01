package rsv.squitv

import android.app.Application
import androidx.datastore.preferences.core.*
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rsv.squitv.data.model.SettingsBackupCodec
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.data.repository.dataStore

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class LegacySettingsCompatibilityTest {
    @Test fun existingFlagsAndImportedLegacyFieldsCannotLockOrResetTheInstallation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.dataStore.edit {
            it[stringPreferencesKey("app_pin")] = "1234"
            it[booleanPreferencesKey("app_lock_enabled")] = true
            it[booleanPreferencesKey("use_oled_theme")] = false
            it[stringPreferencesKey("player_engine")] = "IJK"
            it[stringPreferencesKey("local_profiles_v1")] = """[{"id":"default","name":"Perfil existente","pin":"9876"}]"""
            it[stringSetPreferencesKey("hidden_sections")] = setOf("EPG", "MOVIES")
            it[stringPreferencesKey("hidden_categories_map")] = """{"account_live":["hidden"]}"""
            it[stringPreferencesKey("category_order_map")] = """{"account_live":["b","a"]}"""
            it[stringPreferencesKey("last_live_channel_id")] = "42"
            it[stringPreferencesKey("last_live_category")] = "a"
        }
        val repository = SettingsRepository(context)
        val before = repository.settingsFlow.first()
        assertTrue(before.groupLiveChannels)
        assertEquals("AUTO", before.preferredChannelQuality)
        assertEquals("Perfil existente", before.profiles.single().name)
        assertEquals(setOf("MOVIES"), before.hiddenSections)
        val legacy = SettingsBackupCodec.decode("""{"format":"SquiTV.Settings","schemaVersion":1,"createdAt":1,
            "preferences":{"playerEngine":"VLC","useOledTheme":false,"appPin":"9999","appLockEnabled":true,
            "hideBlockedCategories":true,"bufferStrategy":"Fast","autoPlayEnabled":false}}""")
        repository.importSettingsBackup(legacy)
        val after = repository.settingsFlow.first()
        assertFalse(after.autoPlayEnabled)
        assertEquals(before.categoryOrderMap, after.categoryOrderMap)
        assertEquals(before.hiddenCategoriesMap, after.hiddenCategoriesMap)
        assertEquals("42", after.lastLiveChannelId)
        assertEquals("a", after.lastLiveCategory)
        assertEquals(before.profiles, after.profiles)
        val exported = repository.exportSettingsBackup()
        listOf("appPin", "appLockEnabled", "useOledTheme", "playerEngine", "hideBlockedCategories", "bufferStrategy")
            .forEach { assertFalse(exported.contains(it)) }
        repository.updateGroupLiveChannels(false)
        repository.updatePreferredChannelQuality("HD")
        val restored = SettingsBackupCodec.decode(repository.exportSettingsBackup())
        assertEquals(false, restored.preferences.groupLiveChannels)
        assertEquals("HD", restored.preferences.preferredChannelQuality)
    }
}
