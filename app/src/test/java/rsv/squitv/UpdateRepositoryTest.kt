package rsv.squitv

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import rsv.squitv.data.repository.SettingsRepository
import rsv.squitv.data.repository.UpdateRepository
import rsv.squitv.data.update.*
import rsv.squitv.di.UpdateModule
import rsv.squitv.domain.model.UpdateCheckResult
import rsv.squitv.shared.update.UpdateArtifact
import rsv.squitv.shared.update.UpdateManifest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class UpdateRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var context: Application
    private lateinit var client: OkHttpClient
    private lateinit var api: GitHubApiService
    private lateinit var settings: SettingsRepository
    private val json = Json { ignoreUnknownKeys = true }
    private val hash = "a".repeat(64)

    @Before fun setUp() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.start()
        context = ApplicationProvider.getApplicationContext()
        // Same HTTPS enforcement as production; only the test certificate/origin is substituted.
        client = UpdateModule.provideUpdateClient().newBuilder()
            .sslSocketFactory(trust.sslSocketFactory(), trust.trustManager)
            .callTimeout(5, TimeUnit.SECONDS).build()
        api = Retrofit.Builder().baseUrl(server.url("/")).client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(GitHubApiService::class.java)
        settings = SettingsRepository(context)
    }
    @After fun tearDown() {
        server.shutdown()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }
    private fun repository(debug: Boolean = false, abis: List<String> = listOf("arm64-v8a")) =
        UpdateRepository(context, api, client, settings,
            InstalledAppInfo("1.20", 21L, "rsv.squitv", debug, 28, abis),
            UpdateUrlPolicy(server.hostName, server.port))

    private fun android(version: String = "1.21", code: Long = 22L) =
        UpdateArtifact("android", "apk", "universal", "SquiTV-v" + version + ".apk", version,
            code, 24, 100L, hash, "rsv.squitv", listOf("arm64-v8a", "armeabi-v7a"))
    private fun windows(version: String = "1.22") =
        UpdateArtifact("windows", "exe", "x64", "SquiTV-v" + version + "-windows-x64.exe", version,
            sizeBytes = 200L, sha256 = "b".repeat(64))
    private fun url(tag: String, name: String) =
        server.url("/Rafael020703/SquiTV/releases/download/" + tag + "/" + name).toString()
    private data class Fixture(val release: GitHubReleaseResponse, val text: String?)
    private fun fixture(artifacts: List<UpdateArtifact> = listOf(android()), tag: String = "v1.21",
                        text: String? = null, legacy: Boolean = false): Fixture {
        val body = if (legacy) null else text ?: json.encodeToString(UpdateManifest(releaseTag = tag, artifacts = artifacts))
        val assets = artifacts.map { GitHubAssetResponse(name = it.assetName, size = it.sizeBytes,
            downloadUrl = url(tag, it.assetName), digest = "sha256:" + it.sha256) }.toMutableList()
        if (body != null) assets.add(GitHubAssetResponse("squitv-update.json", body.toByteArray().size.toLong(),
            url(tag, "squitv-update.json")))
        return Fixture(GitHubReleaseResponse(tagName = tag, name = "Squi TV", assets = assets), body)
    }
    private fun enqueue(vararg fixtures: Fixture) {
        server.enqueue(MockResponse().setBody(json.encodeToString(fixtures.map { it.release })))
        fixtures.mapNotNull { it.text }.forEach { server.enqueue(MockResponse().setBody(it)) }
    }

    @Test fun jointManifestUsesAndroidAndExactHash() = runTest {
        enqueue(fixture(listOf(windows("1.21"), android())))
        val result = repository().checkForUpdates()
        assertTrue(result.toString(), result is UpdateCheckResult.UpdateAvailable)
        val update = (result as UpdateCheckResult.UpdateAvailable).updateInfo
        assertEquals("SquiTV-v1.21.apk", update.apkName)
        assertEquals(hash, update.sha256Digest)
        assertEquals(22L, update.versionCode)
        assertEquals(2, server.requestCount)
    }
    @Test fun windowsOnlyLatestDoesNotHidePreviousAndroid() = runTest {
        enqueue(fixture(listOf(windows()), "v1.22"), fixture())
        val result = repository().checkForUpdates() as UpdateCheckResult.UpdateAvailable
        assertEquals("1.21", result.updateInfo.versionName)
        assertEquals(3, server.requestCount)
    }
    @Test fun invalidManifestRetriesWithoutForceAndThenCachesSuccess() = runTest {
        val repo = repository()
        enqueue(fixture(text = "INVALID_JSON"))
        assertTrue(repo.checkForUpdates() is UpdateCheckResult.Error)
        enqueue(fixture())
        val result = repo.checkForUpdates()
        assertTrue(result.toString(), result is UpdateCheckResult.UpdateAvailable)
        assertEquals(4, server.requestCount)
        assertEquals(result, repo.checkForUpdates())
        assertEquals(4, server.requestCount)
    }
    @Test fun forcedFailureClearsPreviousSuccess() = runTest {
        val repo = repository()
        enqueue(fixture())
        assertTrue(repo.checkForUpdates() is UpdateCheckResult.UpdateAvailable)
        enqueue(fixture(text = "INVALID"))
        assertTrue(repo.checkForUpdates(force = true) is UpdateCheckResult.Error)
        enqueue(fixture())
        assertTrue(repo.checkForUpdates() is UpdateCheckResult.UpdateAvailable)
        assertEquals(6, server.requestCount)
    }
    @Test fun mismatchedTagSizeAndHashAreErrorsNotLegacyFallback() = runTest {
        val normal = fixture()
        val changedTag = normal.copy(text = normal.text!!.replace("v1.21", "v1.22")).let {
            it.copy(release = it.release.copy(assets = it.release.assets.map { asset ->
                if (asset.name == "squitv-update.json") asset.copy(size = it.text!!.toByteArray().size.toLong()) else asset
            }))
        }
        val badSize = normal.copy(release = normal.release.copy(assets = normal.release.assets.map {
            if (it.name.endsWith(".apk")) it.copy(size = 101L) else it
        }))
        val badHash = normal.copy(release = normal.release.copy(assets = normal.release.assets.map {
            if (it.name.endsWith(".apk")) it.copy(digest = "sha256:" + "b".repeat(64)) else it
        }))
        for (bad in listOf(changedTag, badSize, badHash)) {
            enqueue(bad)
            assertTrue(repository().checkForUpdates() is UpdateCheckResult.Error)
        }
    }
    @Test fun malformedSchemaAndMissingPackageAreErrors() = runTest {
        for (bad in listOf(fixture(listOf(android().copy(applicationId = null))),
            fixture(text = "{}"), fixture(text = " "))) {
            enqueue(bad)
            assertTrue(repository().checkForUpdates() is UpdateCheckResult.Error)
        }
    }
    @Test fun sameAndroidVersionInNewWindowsReleaseDoesNotNotify() = runTest {
        enqueue(fixture(listOf(windows(), android("1.20", 21L)), "v1.22"))
        assertTrue(repository().checkForUpdates() is UpdateCheckResult.UpToDate)
    }
    @Test fun selectsHighestAndroidVersionRegardlessOfReleaseOrder() = runTest {
        enqueue(fixture(), fixture(listOf(android("1.22", 23L)), "v1.22"))
        val result = repository().checkForUpdates() as UpdateCheckResult.UpdateAvailable
        assertEquals("1.22", result.updateInfo.versionName)
    }
    @Test fun legacyReleaseWithoutManifestKeepsWorking() = runTest {
        enqueue(fixture(legacy = true))
        val result = repository().checkForUpdates() as UpdateCheckResult.UpdateAvailable
        assertEquals(hash, result.updateInfo.sha256Digest)
        assertEquals(1, server.requestCount)
    }
    @Test fun sidecarForAnotherFileOrConflictingDigestIsRejected() = runTest {
        for (text in listOf(hash + "  windows.exe", "b".repeat(64) + "  SquiTV-v1.21.apk")) {
            val base = fixture(legacy = true)
            val sidecar = GitHubAssetResponse("SquiTV-v1.21.apk.sha256", text.toByteArray().size.toLong(),
                url("v1.21", "SquiTV-v1.21.apk.sha256"))
            enqueue(base.copy(release = base.release.copy(assets = base.release.assets + sidecar)))
            server.enqueue(MockResponse().setBody(text))
            assertTrue(repository().checkForUpdates() is UpdateCheckResult.Error)
        }
    }
    @Test fun httpAndLookalikeOriginsAreRejected() = runTest {
        for (badUrl in listOf("http://localhost.evil.example/update.json",
            "http://127.0.0.1/update.json", "https://example.com/update.json")) {
            val base = fixture()
            enqueue(base.copy(release = base.release.copy(assets = base.release.assets.map {
                if (it.name == "squitv-update.json") it.copy(downloadUrl = badUrl) else it
            }), text = null))
            assertTrue(repository().checkForUpdates() is UpdateCheckResult.Error)
        }
        assertEquals(3, server.requestCount)
    }
    @Test fun incompatibleArmApkIsNotOfferedToX86Device() = runTest {
        enqueue(fixture())
        assertEquals(UpdateCheckResult.NoCompatibleApk, repository(abis = listOf("x86_64")).checkForUpdates())
    }
    @Test fun debugBuildStaysBlockedWhenForced() = runTest {
        val repo = repository(debug = true)
        assertEquals(UpdateCheckResult.DebugBuild("1.20"), repo.checkForUpdates())
        assertEquals(UpdateCheckResult.DebugBuild("1.20"), repo.checkForUpdates(true))
        assertEquals(0, server.requestCount)
    }
    @Test fun secondPageCanContainAndroidAfterWindowsReleases() = runTest {
        val windowsReleases = (1..30).map { GitHubReleaseResponse(tagName = "v1.22", assets = emptyList()) }
        val base = fixture()
        server.enqueue(MockResponse().setBody(json.encodeToString(windowsReleases)))
        server.enqueue(MockResponse().setBody(json.encodeToString(listOf(base.release))))
        server.enqueue(MockResponse().setBody(base.text!!))
        assertTrue(repository().checkForUpdates() is UpdateCheckResult.UpdateAvailable)
        assertEquals(3, server.requestCount)
        assertTrue(server.takeRequest(1, TimeUnit.SECONDS)!!.path!!.endsWith("page=1"))
        assertTrue(server.takeRequest(1, TimeUnit.SECONDS)!!.path!!.endsWith("page=2"))
    }
    @Test fun truncatedHistoryIsNotUpToDateAndRateLimitDoesNotUseLegacy() = runTest {
        val page = json.encodeToString((1..30).map { GitHubReleaseResponse(tagName = "v1.22") })
        server.enqueue(MockResponse().setBody(page))
        server.enqueue(MockResponse().setBody(page))
        assertTrue(repository().checkForUpdates() is UpdateCheckResult.Error)
        assertEquals(2, server.requestCount)
        server.enqueue(MockResponse().setResponseCode(429))
        assertTrue(repository().checkForUpdates() is UpdateCheckResult.Error)
        assertEquals(3, server.requestCount)
    }
    @Test fun primary404UsesLegacyRepositoryOnlyForInitialDiscovery() = runTest {
        val base = fixture(legacy = true)
        val legacy = base.release.copy(assets = base.release.assets.map {
            it.copy(downloadUrl = it.downloadUrl.replace("/SquiTV/", "/MeusCanais/"))
        })
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody(json.encodeToString(listOf(legacy))))
        assertTrue(repository().checkForUpdates() is UpdateCheckResult.UpdateAvailable)
        assertEquals(2, server.requestCount)
    }
    @Test fun httpsCannotRedirectMetadataToHttp() = runTest {
        val base = fixture()
        server.enqueue(MockResponse().setBody(json.encodeToString(listOf(base.release))))
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "http://localhost/update.json"))
        assertTrue(repository().checkForUpdates() is UpdateCheckResult.Error)
        assertEquals(2, server.requestCount)
    }
    @Test fun cancellationAbortsManifestCall() = runTest {
        val base = fixture()
        server.enqueue(MockResponse().setBody(json.encodeToString(listOf(base.release))))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val request = async(Dispatchers.IO) { repository().checkForUpdates() }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        request.cancelAndJoin()
        assertTrue(request.isCancelled)
    }
}
