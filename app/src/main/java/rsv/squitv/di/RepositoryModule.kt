package rsv.squitv.di

import android.content.Context
import rsv.squitv.data.api.XtreamService
import rsv.squitv.data.local.dao.IptvDao
import rsv.squitv.data.network.EpgService
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.core.data.repository.CatalogRepository
import rsv.squitv.core.data.repository.EpgRepository
import rsv.squitv.core.data.repository.StreamRepository
import rsv.squitv.core.data.repository.UserRepository
import rsv.squitv.core.domain.interactor.DnsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideCatalogRepository(
        xtreamService: XtreamService,
        iptvDao: IptvDao,
        epgRepository: EpgRepository
    ): CatalogRepository {
        return CatalogRepository(xtreamService, iptvDao, epgRepository)
    }

    @Provides
    @Singleton
    fun provideUserRepository(iptvDao: IptvDao, settingsRepository: SettingsRepository): UserRepository {
        return UserRepository(iptvDao, settingsRepository)
    }

    @Provides
    @Singleton
    fun provideEpgRepository(
        xtreamService: XtreamService,
        epgService: EpgService,
        iptvDao: IptvDao
    ): EpgRepository {
        return EpgRepository(xtreamService, epgService, iptvDao)
    }

    @Provides
    @Singleton
    fun provideStreamRepository(): StreamRepository {
        return StreamRepository()
    }

    @Provides
    @Singleton
    fun provideDnsManager(@ApiOkHttpClient okHttpClient: OkHttpClient): DnsManager {
        return DnsManager(okHttpClient)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository {
        return SettingsRepository(context)
    }
}
