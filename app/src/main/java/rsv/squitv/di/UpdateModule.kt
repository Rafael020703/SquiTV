package rsv.squitv.di

import android.content.Context
import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import javax.inject.Qualifier
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit
import rsv.squitv.BuildConfig
import rsv.squitv.data.update.InstalledAppInfo
import rsv.squitv.data.update.UpdateUrlPolicy
import rsv.squitv.util.AppVersionProvider

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UpdateOkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {
    @Provides
    @Singleton
    fun provideUpdateUrlPolicy(): UpdateUrlPolicy = UpdateUrlPolicy()

    @Provides
    @Singleton
    @UpdateOkHttpClient
    fun provideUpdateClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followSslRedirects(false)
        .addNetworkInterceptor { chain ->
            if (!chain.request().url.isHttps) throw IOException("Updates require HTTPS")
            chain.proceed(chain.request())
        }.build()

    @Provides
    @Singleton
    fun provideInstalledAppInfo(@ApplicationContext context: Context): InstalledAppInfo =
        InstalledAppInfo(
            versionName = AppVersionProvider.getFormattedVersionName(context),
            versionCode = AppVersionProvider.getVersionCode(context),
            applicationId = context.packageName,
            isDebug = BuildConfig.DEBUG,
            sdkInt = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS.orEmpty().toList()
        )
}
