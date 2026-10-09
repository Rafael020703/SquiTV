package rsv.squitv.di

import android.content.Context
import androidx.room.Room
import rsv.squitv.data.local.AppDatabase
import rsv.squitv.data.local.dao.IptvDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java, "squitv-db"
        ).addMigrations(
            AppDatabase.MIGRATION_11_12,
            AppDatabase.MIGRATION_14_15,
            AppDatabase.MIGRATION_15_16,
            AppDatabase.MIGRATION_16_17,
            AppDatabase.MIGRATION_21_22,
            AppDatabase.MIGRATION_22_23,
            AppDatabase.MIGRATION_23_24,
            AppDatabase.MIGRATION_24_25
        ).build()
    }

    @Provides
    @Singleton
    fun provideIptvDao(database: AppDatabase): IptvDao {
        return database.iptvDao()
    }
}
