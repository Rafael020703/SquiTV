package rsv.squitv.data.repository

import android.content.Context
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.HttpException
import rsv.squitv.R
import rsv.squitv.data.update.GitHubApiService
import rsv.squitv.data.update.GitHubAssetResponse
import rsv.squitv.data.update.GitHubReleaseResponse
import rsv.squitv.data.update.GitHubUpdateConfig
import rsv.squitv.data.update.InstalledAppInfo
import rsv.squitv.data.update.UpdateUrlPolicy
import rsv.squitv.di.UpdateOkHttpClient
import rsv.squitv.domain.model.AppUpdateInfo
import rsv.squitv.domain.model.UpdateCheckResult
import rsv.squitv.shared.update.UpdateManifest
import rsv.squitv.shared.update.UpdateChannelReference
import rsv.squitv.shared.update.PlatformUpdateNotes
import rsv.squitv.shared.update.UpdateManifestValidator
import rsv.squitv.shared.update.VersionComparator
import timber.log.Timber

@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gitHubApiService: GitHubApiService,
    @UpdateOkHttpClient private val okHttpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository,
    private val installedAppInfo: InstalledAppInfo,
    private val urlPolicy: UpdateUrlPolicy
) {
    val currentVersionName: String get() = installedAppInfo.versionName
    val currentVersionCode: Long get() = installedAppInfo.versionCode

    private val checkMutex = Mutex()
    private var cachedResult: UpdateCheckResult? = null
    private var checkedAt = 0L
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun fetchText(
        asset: GitHubAssetResponse, repository: String, tag: String, limit: Long
    ): String {
        require(asset.size in 1..limit) { "Invalid metadata asset size" }
        val request = Request.Builder().url(urlPolicy.assetUrl(asset.downloadUrl, repository, tag, asset.name)).build()
        return requireNotNull(fetchMetadata(request, limit, asset.size))
    }

    private suspend fun fetchMetadata(request: Request, limit: Long, expectedSize: Long? = null, allowMissing: Boolean = false): String? =
        kotlinx.coroutines.withTimeout(45_000L) { suspendCancellableCoroutine { continuation ->
            val call = okHttpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (allowMissing && it.code == 404) return@use null
                            check(it.isSuccessful) { "Metadata download failed: HTTP " + it.code }
                            val source = it.body.source()
                            source.request(limit + 1)
                            check(source.buffer.size <= limit) { "Metadata response exceeds limit" }
                            val bytes = source.readByteArray()
                            check(expectedSize == null || bytes.size.toLong() == expectedSize) { "Metadata asset size mismatch" }
                            bytes.toString(Charsets.UTF_8).also { text ->
                                check(text.isNotBlank()) { "Empty metadata response" }
                            }
                        }
                    }
                    if (continuation.isActive) continuation.resumeWith(result)
                }
            })
        } }

    private data class Discovery(val repository: String, val releases: List<GitHubReleaseResponse>, val truncated: Boolean, val reference: UpdateChannelReference? = null)

    private suspend fun discover(): Discovery {
        val first: List<GitHubReleaseResponse>
        val repository: String
        try {
            first = gitHubApiService.getReleases(repo = GitHubUpdateConfig.PRIMARY_REPO, perPage = 30, page = 1)
            repository = GitHubUpdateConfig.PRIMARY_REPO
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
            val legacy = gitHubApiService.getReleases(repo = GitHubUpdateConfig.LEGACY_REPO, perPage = 30, page = 1)
            if (legacy.size < 30) return indexedDiscovery(GitHubUpdateConfig.LEGACY_REPO, legacy, false)
            val second = gitHubApiService.getReleases(repo = GitHubUpdateConfig.LEGACY_REPO, perPage = 30, page = 2)
            return indexedDiscovery(GitHubUpdateConfig.LEGACY_REPO, legacy + second, second.size == 30)
        }
        if (first.size < 30) return indexedDiscovery(repository, first, false)
        val second = gitHubApiService.getReleases(repo = repository, perPage = 30, page = 2)
        return indexedDiscovery(repository, first + second, second.size == 30)
    }

    private suspend fun indexedDiscovery(repository: String, releases: List<GitHubReleaseResponse>, truncated: Boolean): Discovery {
        if (!truncated && releases.count { release -> release.assets.any { it.name == "squitv-update.json" } } <= 20) return Discovery(repository, releases, false)
        // Resolve the platform directly when either history or manifest processing would exceed its bound.
        val request = Request.Builder().url(urlPolicy.channelIndexUrl(repository))
            .header("Accept", "application/vnd.github.raw+json").build()
        val text = fetchMetadata(request, 4096, allowMissing = true) ?: return Discovery(repository, releases, truncated)
        val reference = json.decodeFromString<UpdateChannelReference>(text)
        require(reference.validate("android")) { "Invalid Android update reference" }
        val release = gitHubApiService.getReleaseByTag(repo = repository, tag = reference.releaseTag)
        require(!release.isDraft && !release.isPrerelease && release.tagName == reference.releaseTag) { "Invalid indexed release" }
        return Discovery(repository, listOf(release), false, reference)
    }

    private fun digest(asset: GitHubAssetResponse): String? = asset.digest?.let {
        require(it.matches(Regex("sha256:[a-fA-F0-9]{64}"))) { "Unsupported asset digest" }
        it.substringAfter(':').lowercase()
    }

    private suspend fun checksum(
        release: GitHubReleaseResponse, asset: GitHubAssetResponse, repository: String, expected: String? = null
    ): String? {
        val values = mutableListOf<String>()
        expected?.let { values.add(it.lowercase()) }
        digest(asset)?.let(values::add)
        release.assets.singleOrNull { it.name == asset.name + ".sha256" }?.let { sidecar ->
            val text = fetchText(sidecar, repository, release.tagName, 4096).trim()
            require(text.lines().size == 1) { "Ambiguous checksum file" }
            val parts = text.split(Regex("\\s+"), limit = 2)
            require(parts[0].matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid checksum" }
            require(parts.size == 1 || parts[1].removePrefix("*") == asset.name) { "Checksum belongs to another asset" }
            values.add(parts[0].lowercase())
        }
        require(values.distinct().size <= 1) { "Conflicting asset checksums" }
        return values.firstOrNull()
    }

    private fun better(candidate: AppUpdateInfo, current: AppUpdateInfo?): Boolean {
        if (current == null) return true
        if (candidate.versionCode != null && current.versionCode != null) {
            val code = candidate.versionCode.compareTo(current.versionCode)
            if (code != 0) return code > 0
        }
        return VersionComparator.compareVersions(candidate.versionName, current.versionName) > 0
    }

    suspend fun checkForUpdates(force: Boolean = false): UpdateCheckResult = checkMutex.withLock {
        if (installedAppInfo.isDebug) return@withLock UpdateCheckResult.DebugBuild(currentVersionName)
        cachedResult?.let {
            if (!force && SystemClock.elapsedRealtime() - checkedAt < TimeUnit.HOURS.toMillis(12)) return@withLock it
        }
        withContext(Dispatchers.IO) {
            try {
                require(VersionComparator.isValidVersion(currentVersionName)) { "Invalid installed version" }
                val discovery = discover()
                var selected: AppUpdateInfo? = null
                var compatibleAndroid = false
                var androidPresent = false
                var manifestCount = 0
                for (release in discovery.releases.filter { !it.isDraft && !it.isPrerelease }) {
                    require(release.assets.map { it.name }.distinct().size == release.assets.size) { "Duplicate release assets" }
                    val manifestAsset = release.assets.singleOrNull { it.name == "squitv-update.json" }
                    val apks = release.assets.filter { it.name.endsWith(".apk") && !it.name.contains("debug", ignoreCase = true) }
                    require(discovery.reference == null || manifestAsset != null) { "Indexed manifest missing" }
                    if (manifestAsset == null && apks.isEmpty()) continue
                    require(VersionComparator.isValidVersion(release.tagName)) { "Invalid release tag" }
                    if (VersionComparator.compareVersions(release.tagName, currentVersionName) < 0) continue

                    var candidate: AppUpdateInfo? = null
                    if (manifestAsset != null) {
                        check(++manifestCount <= 20) { "Metadata discovery limit reached" }
                        val manifestText = fetchText(manifestAsset, discovery.repository, release.tagName, 262_144)
                        val manifest = json.decodeFromString<UpdateManifest>(manifestText)
                        discovery.reference?.let { require(it.matches(manifestText, manifest)) { "Indexed manifest mismatch" } }
                        require(UpdateManifestValidator.validate(manifest) && manifest.releaseTag == release.tagName) {
                            "Invalid manifest or release association"
                        }
                        for (artifact in manifest.artifacts) {
                            val asset = release.assets.singleOrNull { it.name == artifact.assetName }
                            require(asset != null && asset.size == artifact.sizeBytes) { "Manifest asset mismatch" }
                            urlPolicy.assetUrl(asset.downloadUrl, discovery.repository, release.tagName, asset.name)
                            digest(asset)?.let { require(it.equals(artifact.sha256, ignoreCase = true)) { "Manifest digest mismatch" } }
                        }
                        androidPresent = androidPresent || manifest.artifacts.any { it.platform == "android" }
                        fun select(version: String, code: Long) = UpdateManifestValidator.selectAndroidArtifact(
                            manifest, version, code, installedAppInfo.sdkInt,
                            installedAppInfo.supportedAbis, installedAppInfo.applicationId
                        )
                        compatibleAndroid = compatibleAndroid || select("0", 0L) != null
                        select(currentVersionName, currentVersionCode)?.let { artifact ->
                            val asset = release.assets.single { it.name == artifact.assetName }
                            candidate = AppUpdateInfo(
                                artifact.versionName, artifact.versionCode, release.name.orEmpty(), release.tagName,
                                release.publishedAt.orEmpty(), PlatformUpdateNotes.select(manifest, "android", release.body.orEmpty()), release.htmlUrl,
                                asset.downloadUrl, asset.name, artifact.sizeBytes,
                                checksum(release, asset, discovery.repository, artifact.sha256)
                            )
                        }
                    } else {
                        val version = release.tagName.removePrefix("v").removePrefix("V")
                        val name = "SquiTV-v" + version + ".apk"
                        val eligible = apks.filter { it.name == name }
                        require(eligible.size == 1 && apks.size == 1) { "Ambiguous or unsupported legacy APK" }
                        val asset = eligible.single()
                        require(asset.size > 0) { "Empty APK" }
                        urlPolicy.assetUrl(asset.downloadUrl, discovery.repository, release.tagName, asset.name)
                        androidPresent = true
                        compatibleAndroid = true
                        if (VersionComparator.isUpdateAvailable(currentVersionName, version)) {
                            candidate = AppUpdateInfo(
                                versionName = version, releaseName = release.name.orEmpty(), tagName = release.tagName,
                                publishedAt = release.publishedAt.orEmpty(), changelog = release.body.orEmpty(),
                                releaseUrl = release.htmlUrl, apkUrl = asset.downloadUrl, apkName = asset.name,
                                apkSize = asset.size, sha256Digest = checksum(release, asset, discovery.repository)
                            )
                        }
                    }
                    candidate?.let { if (better(it, selected)) selected = it }
                }
                val result = when {
                    selected != null -> UpdateCheckResult.UpdateAvailable(selected)
                    discovery.truncated -> error("Release discovery incomplete")
                    androidPresent && !compatibleAndroid -> UpdateCheckResult.NoCompatibleApk
                    else -> UpdateCheckResult.UpToDate(currentVersionName)
                }
                settingsRepository.updateLastUpdateCheck(System.currentTimeMillis(),
                    (result as? UpdateCheckResult.UpdateAvailable)?.updateInfo?.versionName)
                if (result is UpdateCheckResult.UpToDate || result is UpdateCheckResult.UpdateAvailable) {
                    cachedResult = result
                    checkedAt = SystemClock.elapsedRealtime()
                }
                result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never retain a previous success after a forced discovery has failed.
                cachedResult = null
                Timber.w(e, "Update discovery failed")
                UpdateCheckResult.Error(context.getString(R.string.updater_check_failed))
            }
        }
    }
}
