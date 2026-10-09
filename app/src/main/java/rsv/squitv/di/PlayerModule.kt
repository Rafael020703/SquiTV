package rsv.squitv.di

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import rsv.squitv.IptvApplication
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlaybackCache

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadCache

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    @OptIn(UnstableApi::class)
    fun provideBandwidthMeter(@ApplicationContext context: Context): DefaultBandwidthMeter {
        return DefaultBandwidthMeter.Builder(context).build()
    }

    @Provides
    @Singleton
    @OptIn(UnstableApi::class)
    @DownloadCache
    fun provideDownloadCache(@ApplicationContext context: Context): SimpleCache {
        return (context as IptvApplication).downloadCache
    }

    @Provides
    @Singleton
    @OptIn(UnstableApi::class)
    @PlaybackCache
    fun providePlaybackCache(@ApplicationContext context: Context): SimpleCache {
        return (context as IptvApplication).playbackCache
    }

    @Provides
    @OptIn(UnstableApi::class)
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        @PlayerOkHttpClient okHttpClient: OkHttpClient,
        bandwidthMeter: DefaultBandwidthMeter,
        @PlaybackCache playbackCache: SimpleCache
    ): ExoPlayer {
        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(rsv.squitv.data.network.RetrofitClient.USER_AGENT)

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(playbackCache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // Bypasses CacheDataSource for Live IPTV streams to prevent stale packet reads and A/V desync
        val dataSourceFactory = DataSource.Factory {
            val httpDataSource = httpDataSourceFactory.createDataSource()
            val cacheDataSource = cacheDataSourceFactory.createDataSource()

            object : DataSource by httpDataSource {
                private var activeDataSource: DataSource = httpDataSource

                override fun open(dataSpec: DataSpec): Long {
                    val uriPath = dataSpec.uri.path?.lowercase() ?: ""
                    val uriString = dataSpec.uri.toString().lowercase()

                    val isLiveStream = uriPath.contains("/live/") || uriString.contains("/live/") || uriPath.endsWith(".ts")
                    activeDataSource = if (isLiveStream) {
                        httpDataSource
                    } else {
                        cacheDataSource
                    }
                    return activeDataSource.open(dataSpec)
                }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    return activeDataSource.read(buffer, offset, length)
                }

                override fun close() {
                    activeDataSource.close()
                }

                override fun getUri(): Uri? {
                    return activeDataSource.uri
                }

                override fun getResponseHeaders(): Map<String, List<String>> {
                    return activeDataSource.responseHeaders
                }

                override fun addTransferListener(transferListener: TransferListener) {
                    httpDataSource.addTransferListener(transferListener)
                    cacheDataSource.addTransferListener(transferListener)
                }
            }
        }

        // Optimize TS extractors for IPTV
        val extractorsFactory = DefaultExtractorsFactory()
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES)
            .setTsExtractorTimestampSearchBytes(TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES)

        val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)
            .setDataSourceFactory(dataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                3000,  // minBufferMs (3s safety buffer to absorb temporary server drops)
                30000, // maxBufferMs (30s max buffer)
                500,   // bufferForPlaybackMs (ultra-fast 500ms startup latency for Live IPTV)
                1500   // bufferForPlaybackAfterRebufferMs (1.5s after rebuffer before resuming)
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(1000, true) // Retain 1s back-buffer from keyframe for seamless decoder recovery
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setBandwidthMeter(bandwidthMeter)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .build()
    }
}

