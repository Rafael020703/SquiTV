package rsv.squitv.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import rsv.squitv.data.api.XtreamService
import rsv.squitv.data.network.EpgService
import rsv.squitv.data.network.RetrofitClient
import rsv.squitv.data.update.GitHubApiService
import rsv.squitv.data.update.GitHubUpdateConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiOkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlayerOkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    @ApiOkHttpClient
    fun provideOkHttpClient(): OkHttpClient {
        return RetrofitClient.okHttpClient
    }

    @Provides
    @Singleton
    @PlayerOkHttpClient
    fun providePlayerOkHttpClient(): OkHttpClient {
        return RetrofitClient.playerOkHttpClient
    }

    @Provides
    @Singleton
    fun provideXtreamService(@ApiOkHttpClient okHttpClient: OkHttpClient): XtreamService {
        return RetrofitClient.createJsonService("http://ded30.com/")
    }

    @Provides
    @Singleton
    fun provideEpgService(@ApiOkHttpClient okHttpClient: OkHttpClient): EpgService {
        return RetrofitClient.createXmlService("http://ded30.com/")
    }

    @Provides
    @Singleton
    fun provideGitHubApiService(@UpdateOkHttpClient okHttpClient: OkHttpClient): GitHubApiService {
        val contentType = "application/json".toMediaType()
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
        return Retrofit.Builder()
            .baseUrl(GitHubUpdateConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(GitHubApiService::class.java)
    }
}
