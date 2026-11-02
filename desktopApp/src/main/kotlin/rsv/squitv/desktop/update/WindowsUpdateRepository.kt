package rsv.squitv.desktop.update

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.*
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import rsv.squitv.data.update.*
import rsv.squitv.shared.update.*

internal data class WindowsUpdate(val artifact: UpdateArtifact, val asset: GitHubAssetResponse,
    val tag: String, val notes: String, val publishedAt: String?)

/** Dedicated public GitHub transport. Never shares IPTV headers, credentials, cookies or redirects. */
internal class WindowsUpdateRepository(private val directory: Path) : AutoCloseable {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(10, TimeUnit.MINUTES)
        .followRedirects(false).followSslRedirects(false).build()
    private val owner = GitHubUpdateConfig.OWNER
    private val repo = GitHubUpdateConfig.PRIMARY_REPO
    private val cdnHosts = setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com", "github-releases.githubusercontent.com")
    private val maximumInstaller = 600L * 1024 * 1024

    suspend fun discover(current: String, architecture: String): WindowsUpdate? {
        val releases = mutableListOf<GitHubReleaseResponse>()
        var truncated = false
        for (page in 1..2) {
            val url = "${GitHubUpdateConfig.API_BASE_URL}repos/$owner/$repo/releases?per_page=30&page=$page".toHttpUrl()
            val batch = json.decodeFromString<List<GitHubReleaseResponse>>(text(url, 4L * 1024 * 1024))
            releases += batch
            truncated = page == 2 && batch.size == 30
            if (batch.size < 30) break
        }
        var reference: UpdateChannelReference? = null
        var eligibleReleases: List<GitHubReleaseResponse> = releases
        if (truncated || releases.count { release -> release.assets.any { it.name == "squitv-update.json" } } > 20) {
            val indexUrl = "${GitHubUpdateConfig.API_BASE_URL}repos/$owner/$repo/contents/updates/stable/windows.json".toHttpUrl()
            val indexText = withTimeout(45_000L) {
                transfer(indexUrl, accept = "application/vnd.github.raw+json", allowMissing = true) { response, call ->
                    if (response.code == 404) null else readText(response, call, 4096)
                }
            }
            if (indexText != null) {
                val decoded = json.decodeFromString<UpdateChannelReference>(indexText)
                check(decoded.validate("windows")) { "Invalid Windows update reference" }
                reference = decoded
                val url = "${GitHubUpdateConfig.API_BASE_URL}repos/$owner/$repo/releases/tags/${decoded.releaseTag}".toHttpUrl()
                val release = json.decodeFromString<GitHubReleaseResponse>(text(url, 4L * 1024 * 1024))
                check(!release.isDraft && !release.isPrerelease && release.tagName == decoded.releaseTag) { "Invalid indexed release" }
                eligibleReleases = listOf(release)
                truncated = false
            }
        }
        var candidate: WindowsUpdate? = null
        var manifests = 0
        for (release in eligibleReleases.filter { !it.isDraft && !it.isPrerelease && VersionComparator.isValidVersion(it.tagName) }
            .sortedWith { a, b -> VersionComparator.compareVersions(b.tagName, a.tagName) }) {
            currentCoroutineContext().ensureActive()
            if (VersionComparator.compareVersions(release.tagName, current) < 0) continue
            val matches = release.assets.filter { it.name == "squitv-update.json" }
            check(reference == null || matches.size == 1) { "Indexed manifest missing" }
            if (matches.isEmpty()) continue // APK-only/legacy Android releases do not become Windows updates.
            check(matches.size == 1 && manifests++ < 20) { "Manifest discovery limit" }
            val manifestAsset = matches.single()
            val manifestText = text(assetUrl(manifestAsset, release.tagName), 256L * 1024, manifestAsset.size)
            val manifest = json.decodeFromString<UpdateManifest>(manifestText)
            reference?.let { check(it.matches(manifestText, manifest)) { "Indexed manifest mismatch" } }
            check(UpdateManifestValidator.validate(manifest) && manifest.releaseTag == release.tagName) { "Invalid manifest" }
            for (artifact in manifest.artifacts) {
                val asset = release.assets.singleOrNull { it.name == artifact.assetName } ?: error("Missing asset")
                check(asset.size == artifact.sizeBytes) { "Asset size mismatch" }
                assetUrl(asset, release.tagName)
                asset.digest?.takeIf(String::isNotBlank)?.let { digest ->
                    check(digest.equals("sha256:${artifact.sha256}", true)) { "Digest mismatch" }
                }
            }
            val artifact = UpdateManifestValidator.selectWindowsArtifact(manifest, current, architecture) ?: continue
            check(artifact.sizeBytes <= maximumInstaller) { "Installer too large" }
            val asset = release.assets.single { it.name == artifact.assetName }
            val sidecars = release.assets.filter { it.name == artifact.assetName + ".sha256" }
            check(sidecars.size <= 1) { "Ambiguous checksum" }
            sidecars.singleOrNull()?.let { sidecar ->
                val line = text(assetUrl(sidecar, release.tagName), 1024, sidecar.size).trim()
                val hash = Regex("""([a-fA-F0-9]{64})(?:[ \t]+\*?([A-Za-z0-9._-]+))?""").matchEntire(line)
                    ?: error("Invalid checksum")
                check(hash.groupValues[1].equals(artifact.sha256, true) &&
                    (hash.groupValues[2].isEmpty() || hash.groupValues[2] == artifact.assetName)) { "Checksum mismatch" }
            }
            if (candidate == null || VersionComparator.compareVersions(artifact.versionName, candidate.artifact.versionName) > 0)
                candidate = WindowsUpdate(artifact, asset, release.tagName, PlatformUpdateNotes.select(manifest, "windows", release.body.orEmpty()), release.publishedAt)
        }
        check(candidate != null || !truncated) { "Incomplete release discovery" }
        return candidate
    }

    fun assetUrl(asset: GitHubAssetResponse, tag: String): HttpUrl {
        val url = asset.downloadUrl.toHttpUrl()
        check(url.isHttps && url.host == "github.com" && url.port == 443 && url.username.isEmpty() &&
            url.password.isEmpty() && url.query == null && url.fragment == null &&
            url.pathSegments == listOf(owner, repo, "releases", "download", tag, asset.name)) { "Invalid asset URL" }
        return url
    }

    private fun allowed(url: HttpUrl): Boolean = url.isHttps && url.port == 443 &&
        url.username.isEmpty() && url.password.isEmpty() && url.fragment == null &&
        (url.host == "api.github.com" || url.host in cdnHosts)

    private class Redirect(val target: HttpUrl) : IOException()

    private suspend fun <T> transfer(initial: HttpUrl, accept: String = "application/vnd.github+json", allowMissing: Boolean = false, read: (Response, Call) -> T): T {
        var url = initial
        val seen = mutableSetOf<HttpUrl>()
        repeat(6) {
            check(allowed(url) && seen.add(url)) { "Invalid HTTPS redirect" }
            try {
                val request = Request.Builder().url(url).header("Accept", accept)
                    .header("User-Agent", "SquiTV-Windows-Updater").build()
                return suspendCancellableCoroutine { continuation ->
                    val call = client.newCall(request)
                    continuation.invokeOnCancellation { call.cancel() }
                    call.enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            if (continuation.isActive) continuation.resumeWith(Result.failure(e))
                        }
                        override fun onResponse(call: Call, response: Response) {
                            val result = runCatching { response.use {
                                if (it.code in setOf(301, 302, 303, 307, 308)) throw Redirect(
                                    it.header("Location")?.let(url::resolve) ?: error("Missing redirect"))
                                check(it.code == 200 || allowMissing && it.code == 404) { "GitHub request failed" }
                                read(it, call)
                            } }
                            if (continuation.isActive) continuation.resumeWith(result)
                        }
                    })
                }
            } catch (redirect: Redirect) { url = redirect.target }
        }
        error("Too many redirects")
    }

    private suspend fun text(url: HttpUrl, limit: Long, expected: Long? = null): String {
        check(expected == null || expected in 1..limit) { "Invalid metadata size" }
        return withTimeoutOrNull(45_000L) {
            transfer(url) { response, call ->
                readText(response, call, limit, expected)
            }
        } ?: error("Metadata timeout")
    }

    private fun readText(response: Response, call: Call, limit: Long, expected: Long? = null): String {
        check(response.body.contentLength() <= limit) { "Metadata too large" }
        val output = ByteArrayOutputStream()
        response.body.byteStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                if (call.isCanceled()) throw IOException()
                val count = input.read(buffer); if (count < 0) break
                check(output.size() + count <= limit) { "Metadata too large" }
                output.write(buffer, 0, count)
            }
        }
        check(expected == null || output.size().toLong() == expected) { "Metadata size mismatch" }
        return output.toString(Charsets.UTF_8).also { check(it.isNotBlank()) }
    }

    suspend fun download(update: WindowsUpdate, progress: (Long) -> Unit): Path = withContext(Dispatchers.IO) {
        val artifact = update.artifact
        check(artifact.sizeBytes in 1..maximumInstaller)
        Files.createDirectories(directory)
        val destination = directory.resolve(artifact.assetName).normalize()
        check(destination.parent == directory.normalize() && artifact.assetName == "SquiTV-${artifact.versionName}.exe")
        if (verified(destination, artifact)) return@withContext destination
        transfer(assetUrl(update.asset, update.tag)) { response, call ->
            val length = response.body.contentLength()
            check(length < 0 || length == artifact.sizeBytes) { "Installer size mismatch" }
            val temporary = Files.createTempFile(directory, "squitv-update-", ".part")
            var moved = false
            try {
                var count = 0L; var lastReport = 0L
                val digest = MessageDigest.getInstance("SHA-256")
                response.body.byteStream().use { input -> Files.newOutputStream(temporary).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (call.isCanceled()) throw IOException()
                        val n = input.read(buffer); if (n < 0) break
                        count += n; check(count <= artifact.sizeBytes) { "Installer too large" }
                        digest.update(buffer, 0, n); output.write(buffer, 0, n)
                        val now = System.nanoTime()
                        if (now - lastReport >= 250_000_000L) { progress(count); lastReport = now }
                    }
                } }
                check(count == artifact.sizeBytes && MessageDigest.isEqual(digest.digest(), HexFormat.of().parseHex(artifact.sha256))) { "Installer integrity failed" }
                if (call.isCanceled()) throw IOException()
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING)
                moved = true; progress(count); destination
            } finally { if (!moved) Files.deleteIfExists(temporary) }
        }
    }

    suspend fun verified(file: Path, artifact: UpdateArtifact): Boolean {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) != artifact.sizeBytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            if (input.read() != 0x4d || input.read() != 0x5a) return false
        }
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { currentCoroutineContext().ensureActive(); val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return MessageDigest.isEqual(digest.digest(), HexFormat.of().parseHex(artifact.sha256))
    }

    override fun close() { client.dispatcher.cancelAll(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
}
