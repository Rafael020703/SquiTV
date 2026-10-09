package rsv.squitv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import androidx.media3.common.util.UnstableApi
import coil.decode.DataSource
import coil.request.CachePolicy
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.room.Room
import timber.log.Timber
import rsv.squitv.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.util.concurrent.Executors
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.core.debug.DebugLogger
import rsv.squitv.core.debug.DebugExceptionHandler
import rsv.squitv.core.debug.DebugLifecycleObserver
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

@HiltAndroidApp
@UnstableApi
class IptvApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20) // Reduced to 20% to leave room for ExoPlayer
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(512 * 1024 * 1024L) // Increased to 512MB for better persistence
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false) // Vital for IPTV providers with bad headers
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface IptvApplicationEntryPoint {
        fun database(): AppDatabase
    }

    private val database by lazy {
        EntryPointAccessors.fromApplication(this, IptvApplicationEntryPoint::class.java).database()
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressPollingJob: Job? = null
    private val lastDownloadedBytes = mutableMapOf<String, Long>()
    private val lastPollTime = mutableMapOf<String, Long>()

    private val databaseProvider by lazy { StandaloneDatabaseProvider(this) }
    private val downloadDirectory by lazy {
        val downloadFolder = getExternalFilesDir(null) ?: filesDir
        File(downloadFolder, "downloads")
    }

    val downloadCache by lazy {
        SimpleCache(downloadDirectory, NoOpCacheEvictor(), databaseProvider)
    }

    private val playbackDirectory by lazy {
        File(cacheDir, "playback_cache")
    }

    val playbackCache by lazy {
        SimpleCache(playbackDirectory, LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024), databaseProvider)
    }

    val downloadManager by lazy {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(rsv.squitv.data.network.RetrofitClient.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        DownloadManager(
            this,
            databaseProvider,
            downloadCache,
            dataSourceFactory,
            Executors.newFixedThreadPool(3)
        ).apply {
            maxParallelDownloads = 2
            // Requirements: Start with any network, can be restricted later via settings
            requirements = Requirements(0) // Default: any network
            
            addListener(object : DownloadManager.Listener {
                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?
                ) {
                    if (finalException != null) {
                        Timber.e(finalException, "Erro no download: %s", download.request.id)
                    }
                    updateDownloadStatus(download)
                    startProgressPollingIfNeeded()
                }

                override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                    val id = download.request.id
                    lastDownloadedBytes.remove(id)
                    lastPollTime.remove(id)
                    applicationScope.launch(Dispatchers.IO) {
                        database.iptvDao().deleteDownload(id.toInt())
                    }
                }
            })
        }
    }

    private fun startProgressPollingIfNeeded() {
        val hasActiveDownloads = downloadManager.currentDownloads.any { 
            it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_RESTARTING 
        }

        if (hasActiveDownloads && progressPollingJob == null) {
            progressPollingJob = applicationScope.launch {
                while (true) {
                    val activeDownloads = downloadManager.currentDownloads.filter {
                        it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_RESTARTING
                    }
                    if (activeDownloads.isEmpty()) break

                    activeDownloads.forEach { download ->
                        updateDownloadStatus(download)
                    }
                    delay(1000L)
                }
                progressPollingJob = null
            }
        }
    }

    private fun updateDownloadStatus(download: Download) {
        val status = when (download.state) {
            Download.STATE_COMPLETED -> "COMPLETED"
            Download.STATE_FAILED -> "FAILED"
            Download.STATE_DOWNLOADING -> "DOWNLOADING"
            Download.STATE_QUEUED -> "PENDING"
            Download.STATE_REMOVING -> "REMOVING"
            Download.STATE_RESTARTING -> "DOWNLOADING"
            Download.STATE_STOPPED -> "STOPPED"
            else -> "UNKNOWN"
        }

        val id = download.request.id
        val now = System.currentTimeMillis()
        val currentBytes = download.bytesDownloaded
        
        val lastBytes = lastDownloadedBytes[id] ?: 0L
        val lastTime = lastPollTime[id] ?: 0L
        
        var speedMbps = 0.0
        if (status == "DOWNLOADING" && lastTime > 0) {
            val timeDiffSec = (now - lastTime) / 1000.0
            if (timeDiffSec > 0.1) {
                val bytesDiff = currentBytes - lastBytes
                if (bytesDiff > 0) {
                    speedMbps = (bytesDiff * 8.0 / timeDiffSec) / 1_000_000.0
                }
            }
        }
        
        lastDownloadedBytes[id] = currentBytes
        lastPollTime[id] = now

        applicationScope.launch(Dispatchers.IO) {
            val entity = database.iptvDao().getDownload(download.request.id.toInt())
            if (entity != null) {
                database.iptvDao().insertDownload(
                    entity.copy(
                        status = status,
                        progress = download.percentDownloaded,
                        size = download.contentLength,
                        downloadedBytes = download.bytesDownloaded,
                        downloadSpeedMbps = speedMbps
                    )
                )
            }
        }
    }

    val downloadTracker by lazy {
        rsv.squitv.data.service.DownloadTracker(this)
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            DebugLogger.init(this)
            DebugExceptionHandler.install()
            registerActivityLifecycleCallbacks(DebugLifecycleObserver())
        }

        // Subscribe to global topic for content updates if Firebase messaging is active
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("novidades")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Timber.d("Subscribed to 'novidades' topic successfully")
                    } else {
                        Timber.d("Topic subscription pending future Firebase configuration")
                    }
                }
        } catch (e: Exception) {
            Timber.d(e, "Firebase messaging topic subscription skipped")
        }
        
        applicationScope.launch {
            settingsRepository.settingsFlow.collectLatest { settings ->
                val networkRequirement = if (settings.downloadWifiOnly) {
                    2 // Requirements.NETWORK_UNMETERED
                } else {
                    1 // Requirements.NETWORK_CONNECTED
                }
                downloadManager.requirements = Requirements(networkRequirement)

                // Schedule periodic background sync if not already scheduled
                schedulePeriodicSync(settings.syncIntervalHours)
            }
        }
    }

    private fun schedulePeriodicSync(intervalHours: Int) {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        val syncRequest = androidx.work.PeriodicWorkRequestBuilder<rsv.squitv.worker.SyncWorker>(
            intervalHours.toLong(), java.util.concurrent.TimeUnit.HOURS
        ).setConstraints(constraints)
            .addTag("periodic_sync")
            .build()

        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "global_periodic_sync",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
