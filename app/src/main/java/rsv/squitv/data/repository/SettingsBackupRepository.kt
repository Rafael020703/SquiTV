package rsv.squitv.data.repository

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rsv.squitv.data.local.AppDatabase
import rsv.squitv.data.model.*

data class BackupImportResult(val skippedAccounts: Int, val skippedFavorites: Int)

@Singleton
class SettingsBackupRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val database: AppDatabase
) {
    private val importMutex = Mutex()

    private suspend fun claimLegacyFavorites(settings: SettingsRepository.AppSettings) {
        settings.credentials?.let {
            database.iptvDao().claimLegacyFavorites(
                SettingsBackupCodec.fingerprint(SettingsBackupCodec.accountKey(it)), settings.activeProfileId)
        }
    }

    suspend fun export(selected: Set<BackupOption>): String {
        val settings = settingsRepository.settingsFlow.first()
        claimLegacyFavorites(settings)
        val base = SettingsBackupCodec.decode(settingsRepository.exportSettingsBackup())
        val favorites = if (BackupOption.FAVORITES in selected) database.iptvDao().getAllFavoritesForBackup() else null
        val accounts = base.accounts.associateBy { it.fingerprint }.toMutableMap()
        favorites.orEmpty().forEach { favorite ->
            accounts.putIfAbsent(favorite.accountId, BackupAccount(favorite.accountId, emptyMap()))
        }
        return SettingsBackupCodec.encode(SettingsBackupCodec.select(
            base.copy(accounts = accounts.values.toList(), favorites = favorites), selected))
    }

    suspend fun import(backup: SettingsBackup, selected: Set<BackupOption>): BackupImportResult = importMutex.withLock {
        val incoming = SettingsBackupCodec.select(backup, selected)
        val settings = settingsRepository.settingsFlow.first()
        val configured = SettingsBackupCodec.configuredAccounts(settings)
        val profiles = settings.profiles.map { it.id }.toSet() + incoming.profiles.orEmpty().map { it.id }
        val favorites = incoming.favorites.orEmpty().filter { it.accountId in configured && it.profileId in profiles }
        claimLegacyFavorites(settings)
        // A settings write failure rolls back the Room insert. Existing favourites are merged, never cleared.
        database.withTransaction {
            favorites.chunked(500).forEach { database.iptvDao().insertFavorites(it) }
            settingsRepository.importSettingsBackup(incoming)
        }
        BackupImportResult(incoming.accounts.count { it.fingerprint !in configured }, incoming.favorites.orEmpty().size - favorites.size)
    }
}
