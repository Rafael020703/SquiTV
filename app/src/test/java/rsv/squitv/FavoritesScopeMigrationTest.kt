package rsv.squitv

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rsv.squitv.data.local.AppDatabase
import rsv.squitv.data.local.entities.FavoriteEntity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class FavoritesScopeMigrationTest {
    private lateinit var context: Context
    private lateinit var name: String
    private var database: AppDatabase? = null

    @Before fun prepareVersion25Database() {
        context = ApplicationProvider.getApplicationContext()
        // Keep Robolectric's native SQLite path below Windows MAX_PATH.
        name = "fav-${UUID.randomUUID().toString().take(8)}.db"
        // Other tables are unchanged by 25 -> 26. Start with their actual Room schema.
        val initial = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        try { initial.openHelper.writableDatabase } finally { initial.close() }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { sqlite ->
            sqlite.execSQL("DROP TABLE favorites")
            sqlite.execSQL("""CREATE TABLE favorites (
                streamId INTEGER NOT NULL, streamType TEXT NOT NULL, name TEXT NOT NULL DEFAULT '',
                logo TEXT, rating TEXT, releaseDate TEXT, url TEXT, containerExtension TEXT,
                categoryId TEXT, timestamp INTEGER NOT NULL, PRIMARY KEY(streamId, streamType))""")
            sqlite.execSQL("INSERT INTO favorites(streamId,streamType,name,timestamp) VALUES(101,'LIVE','Canal',1)")
            sqlite.execSQL("INSERT INTO favorites(streamId,streamType,name,timestamp) VALUES(101,'VOD','Filme antigo',10)")
            sqlite.execSQL("INSERT INTO favorites(streamId,streamType,name,timestamp) VALUES(101,'MOVIE','Filme recente',20)")
            sqlite.execSQL("INSERT INTO favorites(streamId,streamType,name,timestamp) VALUES(200,'SERIES','Série',30)")
            sqlite.version = 25
        }
        database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .allowMainThreadQueries().addMigrations(AppDatabase.MIGRATION_25_26).build()
    }

    @After fun closeDatabase() {
        database?.close()
        if (::context.isInitialized && ::name.isInitialized) context.deleteDatabase(name)
    }

    @Test fun migratePreservesData() = runBlocking {
        val db = database!!
        val favorites = db.iptvDao().getAllFavoritesForBackup()
        assertEquals(26, db.openHelper.writableDatabase.version)
        assertEquals(3, favorites.size)
        assertEquals(setOf("LIVE", "MOVIE", "SERIES"), favorites.map { it.streamType }.toSet())
        assertTrue(favorites.all { it.accountId.isEmpty() && it.profileId.isEmpty() })
        assertEquals("Filme recente", favorites.single { it.streamType == "MOVIE" }.name)
        assertEquals(20L, favorites.single { it.streamType == "MOVIE" }.timestamp)
    }

    @Test fun claimLegacyOnce() = runBlocking {
        val dao = database!!.iptvDao()
        val accountA = "a".repeat(64)
        val accountB = "b".repeat(64)
        dao.claimLegacyFavorites(accountA, "profile-a")
        dao.claimLegacyFavorites(accountB, "profile-b")
        assertEquals(3, dao.getScopedFavoritesFlow(accountA, "profile-a").first().size)
        assertTrue(dao.getScopedFavoritesFlow(accountB, "profile-b").first().isEmpty())
        dao.insertFavorites(listOf(
            FavoriteEntity(101, "LIVE", "Outra conta", accountId = accountB, profileId = "profile-a"),
            FavoriteEntity(101, "LIVE", "Outro perfil", accountId = accountA, profileId = "profile-b")))
        assertEquals(5, dao.getAllFavoritesForBackup().size)
        dao.deleteScopedFavorite(accountA, "profile-a", 101, "LIVE")
        assertEquals(4, dao.getAllFavoritesForBackup().size)
        assertEquals(1, dao.getScopedFavoritesFlow(accountB, "profile-a").first().size)
        assertEquals(1, dao.getScopedFavoritesFlow(accountA, "profile-b").first().size)
        assertTrue(dao.getScopedFavoritesFlow(accountA, "profile-a").first().any { it.streamId == 101 && it.streamType == "MOVIE" })
    }
}
