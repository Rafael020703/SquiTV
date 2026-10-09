package rsv.squitv

import rsv.squitv.data.local.AppDatabase
import rsv.squitv.data.local.entities.FavoriteEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RoomMigrationTest {

    @Test
    fun testFavoriteEntity_compositePrimaryKeyStructure() {
        val liveFav = FavoriteEntity(
            streamId = 100,
            streamType = "LIVE",
            name = "Canal 100",
            categoryId = "10"
        )
        val movieFav = FavoriteEntity(
            streamId = 100,
            streamType = "MOVIE",
            name = "Filme 100",
            categoryId = "20"
        )

        assertEquals(100, liveFav.streamId)
        assertEquals("LIVE", liveFav.streamType)
        assertEquals(100, movieFav.streamId)
        assertEquals("MOVIE", movieFav.streamType)
        assertNotNull(liveFav.name)
        assertNotNull(movieFav.name)
    }

    @Test
    fun testMigration24To25_versionsMatch() {
        val migration = AppDatabase.MIGRATION_24_25
        assertEquals(24, migration.startVersion)
        assertEquals(25, migration.endVersion)
    }
}
