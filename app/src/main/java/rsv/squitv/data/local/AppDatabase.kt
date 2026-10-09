package rsv.squitv.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import rsv.squitv.data.local.dao.IptvDao
import rsv.squitv.data.local.entities.*

@Database(
    entities = [
        IptvCategoryEntity::class, 
        IptvStreamEntity::class, 
        IptvStreamFtsEntity::class,
        SeasonEntity::class, 
        EpisodeEntity::class,
        WatchProgressEntity::class,
        DownloadEntity::class,
        SearchHistoryEntity::class,
        EpgReminderEntity::class,
        FavoriteEntity::class,
        EpgProgramEntity::class,
        EpgProgramStagingEntity::class,
        PendingSyncEntity::class
    ], 
    version = 25,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun iptvDao(): IptvDao

    companion object {
        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorites_new` (" +
                    "`streamId` INTEGER NOT NULL, `streamType` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL DEFAULT '', `logo` TEXT, " +
                    "`rating` TEXT, `releaseDate` TEXT, `url` TEXT, " +
                    "`containerExtension` TEXT, `categoryId` TEXT, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`streamId`, `streamType`))"
                )
                database.execSQL(
                    "INSERT OR IGNORE INTO favorites_new (streamId, streamType, name, logo, rating, releaseDate, url, containerExtension, categoryId, timestamp) " +
                    "SELECT streamId, streamType, name, logo, rating, releaseDate, url, containerExtension, categoryId, timestamp FROM favorites"
                )
                database.execSQL("DROP TABLE favorites")
                database.execSQL("ALTER TABLE favorites_new RENAME TO favorites")
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_syncs` (" +
                    "`type` TEXT NOT NULL, `providerHash` TEXT NOT NULL, " +
                    "`contentId` TEXT NOT NULL, `payload` TEXT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`type`, `providerHash`, `contentId`))"
                )
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `epg_programs_staging` (" +
                    "`channelId` TEXT NOT NULL, `startTime` INTEGER NOT NULL, " +
                    "`stopTime` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                    "`description` TEXT, `category` TEXT, " +
                    "PRIMARY KEY(`channelId`, `startTime`))"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_epg_programs_staging_channelId` ON `epg_programs_staging` (`channelId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_epg_programs_staging_startTime` ON `epg_programs_staging` (`startTime`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_epg_programs_staging_stopTime` ON `epg_programs_staging` (`stopTime`)")
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. Create FTS table if not exists (external content)
                database.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `iptv_streams_fts` USING fts4(" +
                    "content=`iptv_streams`, `name`, `categoryId`)"
                )
                // 2. Populate FTS table
                database.execSQL("INSERT INTO `iptv_streams_fts`(`iptv_streams_fts`) VALUES('rebuild')")
                
                // 3. Create composite index for category filtering
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_iptv_streams_type_category` " +
                    "ON `iptv_streams` (`streamType`, `categoryId`)"
                )
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create the new favorites table
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorites` (`streamId` INTEGER NOT NULL, `streamType` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`streamId`))"
                )
                
                // Migrate existing favorites from iptv_streams to the new favorites table
                database.execSQL(
                    "INSERT OR IGNORE INTO favorites (streamId, streamType, timestamp) " +
                    "SELECT id, streamType, ${System.currentTimeMillis()} FROM iptv_streams WHERE isFavorite = 1"
                )
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `iptv_categories` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `favorites` ADD COLUMN `name` TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE `favorites` ADD COLUMN `logo` TEXT")
                database.execSQL("ALTER TABLE `favorites` ADD COLUMN `rating` TEXT")
                database.execSQL("ALTER TABLE `favorites` ADD COLUMN `releaseDate` TEXT")
                database.execSQL("ALTER TABLE `favorites` ADD COLUMN `url` TEXT")
                database.execSQL("ALTER TABLE `favorites` ADD COLUMN `containerExtension` TEXT")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `iptv_streams` ADD COLUMN `plot` TEXT")
                database.execSQL("ALTER TABLE `iptv_streams` ADD COLUMN `duration` TEXT")
                database.execSQL("ALTER TABLE `iptv_streams` ADD COLUMN `backdrop` TEXT")
                database.execSQL("ALTER TABLE `iptv_streams` ADD COLUMN `youtubeTrailer` TEXT")
            }
        }
    }
}
