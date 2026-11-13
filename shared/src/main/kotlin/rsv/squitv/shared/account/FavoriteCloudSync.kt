package rsv.squitv.shared.account

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class CloudFavorite(
    val kind: String,
    val id: Int,
    val title: String? = null,
    val revision: Long = 0,
    val deleted: Boolean = false,
) {
    val key get() = kind to id
}

@Serializable
data class CloudProfile(val id: String, val name: String, val revision: Long)

@Serializable data class CloudGenre(val id: Int, val genre: String? = null)
@Serializable data class CloudGenrePage(val available: Boolean, val items: List<CloudGenre> = emptyList(),
    val next: Long = 0, val hasMore: Boolean = false)
@Serializable data class CloudCatalogStatus(val available: Boolean, val total: Int = 0,
    val prepared: Int = 0, val failed: Int = 0)

@Serializable
data class CloudCategories(
    val kind: String,
    val mode: String,
    val order: List<String>,
    val hidden: List<String>,
    val revision: Long = 0,
)

@Serializable
data class CloudProgress(
    val kind: String,
    val id: Int,
    val catalogId: Int? = null,
    val title: String? = null,
    val positionMs: Long,
    val durationMs: Long? = null,
    val completed: Boolean = false,
    val observedAt: Long,
    val revision: Long = 0,
    val deleted: Boolean = false,
) {
    val key get() = kind to id
}

@Serializable
data class CloudProgressJournal(
    val pending: List<CloudProgress> = emptyList(),
    val downloaded: List<CloudProgress> = emptyList(),
)

@Serializable
data class FavoriteCheckpoint(
    val profile: String, val rows: List<CloudFavorite>, val boundAt: Long = 0,
    val categoryBaseline: List<CloudCategories> = emptyList(),
)

interface FavoriteLocalStore {
    val label: String
    suspend fun checkpoint(): FavoriteCheckpoint?
    suspend fun saveCheckpoint(checkpoint: FavoriteCheckpoint)
    suspend fun snapshot(): List<CloudFavorite>
    suspend fun applyChanges(expected: Set<Pair<String, Int>>, cloudRows: List<CloudFavorite>): Boolean
    suspend fun categorySnapshot(): List<CloudCategories>
    suspend fun applyCategories(expected: List<CloudCategories>, cloudRows: List<CloudCategories>): Boolean
    suspend fun progressJournal(): CloudProgressJournal
    suspend fun saveProgressJournal(journal: CloudProgressJournal)
    suspend fun applyProgress(boundAt: Long, rows: List<CloudProgress>)
    suspend fun applyProfile(profile: CloudProfile) {}
}

object FavoriteCloudSession {
    class Active internal constructor(val localAccount: String, val client: FavoriteCloudClient)

    private val mutable = MutableStateFlow<Active?>(null)
    val state = mutable.asStateFlow()
    private val progressLock = Mutex()
    private val progressRevision = MutableStateFlow(0L)
    val progressUpdates = progressRevision.asStateFlow()
    private val pendingProgressRevision = MutableStateFlow(0L)
    val pendingProgressUpdates = pendingProgressRevision.asStateFlow()

    fun catalogClient(account: String?): FavoriteCloudClient? =
        state.value?.takeIf { it.localAccount == account }?.client

    suspend fun recordProgress(local: FavoriteLocalStore, progress: CloudProgress) {
        if (local.checkpoint() == null) return
        progressLock.withLock {
            val journal = local.progressJournal()
            val previous = journal.pending.firstOrNull { it.key == progress.key }
            if (previous != null && previous.observedAt >= progress.observedAt) return@withLock
            val revision = journal.downloaded.firstOrNull { it.key == progress.key }?.revision ?: 0
            local.saveProgressJournal(journal.copy(
                pending = (journal.pending.filterNot { it.key == progress.key } + progress.copy(revision = revision)).takeLast(5000),
            ))
            progressRevision.value += 1
            pendingProgressRevision.value += 1
        }
    }

    internal suspend fun mergeProgress(
        local: FavoriteLocalStore, sent: List<CloudProgress>, downloaded: List<CloudProgress>,
    ) = progressLock.withLock {
        val journal = local.progressJournal()
        val acknowledged = sent.associate { it.key to it.observedAt }
        local.saveProgressJournal(CloudProgressJournal(
            journal.pending.filter { (acknowledged[it.key] ?: 0) < it.observedAt }, downloaded,
        ))
        progressRevision.value += 1
    }

    fun activate(localAccount: String?, linked: AccountPilot.LinkedLogin?) =
        activateClient(localAccount, linked?.favoriteClient)

    fun restore(localAccount: String?, encryptedDecoded: String?) =
        activateClient(localAccount, FavoriteCloudClient.restore(encryptedDecoded))

    private fun activateClient(localAccount: String?, client: FavoriteCloudClient?) {
        val current = mutable.value
        if (current != null && current.localAccount == localAccount && client != null && current.client.sameSession(client))
            return
        mutable.value = if (localAccount != null && client != null) Active(localAccount, client) else null
    }
}

class FavoriteCloudClient internal constructor(
    private val token: String,
    val scopeId: String,
    private val validUntil: Long = System.currentTimeMillis() + 11 * 60 * 60 * 1000L,
) {
    @Serializable private class StoredSession(val token: String, val scopeId: String, val validUntil: Long)

    fun encodeForProtectedStorage(): String = Json.encodeToString(StoredSession(token, scopeId, validUntil))

    internal fun sameSession(other: FavoriteCloudClient): Boolean =
        token == other.token && scopeId == other.scopeId && validUntil == other.validUntil

    companion object {
        internal fun restore(encoded: String?): FavoriteCloudClient? {
            if (encoded.isNullOrBlank() || encoded.length > 2048) return null
            val session = runCatching { Json.decodeFromString<StoredSession>(encoded) }.getOrNull() ?: return null
            if (session.validUntil <= System.currentTimeMillis() || session.token.length !in 30..512 ||
                !session.scopeId.matches(Regex("[a-f0-9-]{36}"))) return null
            return FavoriteCloudClient(session.token, session.scopeId, session.validUntil)
        }
    }
    @Serializable private data class ProgressBatch(val items: List<CloudProgress>)
    @Serializable private data class ProgressPage(val items: List<CloudProgress>, val next: Long, val hasMore: Boolean)
    @Serializable private data class Categories(val items: List<CloudCategories>)
    @Serializable private data class Profiles(val profiles: List<CloudProfile>)
    @Serializable private data class Page(val items: List<CloudFavorite>, val next: Long, val hasMore: Boolean)
    @Serializable private data class ProfileRequest(val action: String, val name: String)
    @Serializable private data class FavoriteBatch(val action: String, val items: List<CloudFavorite>)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()

    suspend fun profiles(): List<CloudProfile> = json.decodeFromString<Profiles>(call("profiles").toString()).profiles

    suspend fun catalogGenres(kind: String, after: Long): CloudGenrePage? = tryBackgroundCatalog {
        json.decodeFromJsonElement<CloudGenrePage>(catalogRequest(kind, "genres", "after" to after.toString()))
    }

    suspend fun catalogStatus(kind: String): CloudCatalogStatus? = tryBackgroundCatalog {
        json.decodeFromJsonElement<CloudCatalogStatus>(catalogRequest(kind, "status")).takeIf { it.available }
    }

    suspend fun <T> catalogDetail(kind: String, id: Int, serializer: DeserializationStrategy<T>): T? =
        tryBackgroundCatalog {
            val response = catalogRequest(kind, "detail", "id" to id.toString())
            if ((response["available"] as? JsonPrimitive)?.booleanOrNull != true) return@tryBackgroundCatalog null
            response["data"]?.let { json.decodeFromJsonElement(serializer, it) }
        }

    private suspend fun catalogRequest(kind: String, view: String, argument: Pair<String, String>? = null): JsonObject {
        val url = "https://squitv.rafael020703.shop/api/sync/catalog".toHttpUrl().newBuilder()
            .addQueryParameter("kind", kind).addQueryParameter("view", view)
            .apply { argument?.let { (name, value) -> addQueryParameter(name, value) } }.build()
        val (status, response) = AccountPilot.execute(
            Request.Builder().url(url).header("Authorization", "SquiSync " + token).build(), 4L * 1024 * 1024,
        )
        if (status != 200) throw IOException("Catálogo no servidor indisponível (HTTP $status).")
        return response
    }

    private suspend fun <T> tryBackgroundCatalog(action: suspend () -> T): T? = try {
        action()
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: Exception) {
        null
    }

    suspend fun createProfile(name: String): CloudProfile = json.decodeFromString(
        call("profiles", body = json.encodeToJsonElement(ProfileRequest("create", name))).toString()
    )

    /** The first association stays explicit. The caller owns the account and UI lifecycle. */
    @OptIn(FlowPreview::class)
    suspend fun maintainAssociatedData(local: FavoriteLocalStore): Unit = coroutineScope {
        launch {
            FavoriteCloudSession.pendingProgressUpdates.debounce(15_000).collect {
                tryBackgroundSync { syncPendingProgress(local) }
            }
        }
        while (true) {
            tryBackgroundSync {
                val association = local.checkpoint() ?: return@tryBackgroundSync
                val profile = profiles().firstOrNull { it.id == association.profile } ?: return@tryBackgroundSync
                tryBackgroundSync { sync(profile, local, allowImport = false) }
                syncProgress(profile, local)
            }
            delay(60_000)
        }
    }

    private suspend fun tryBackgroundSync(action: suspend () -> Unit) {
        try {
            action()
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            // Leave pending changes intact for the next attempt or for manual sync.
        }
    }

    suspend fun sync(profile: CloudProfile, local: FavoriteLocalStore, allowImport: Boolean): Int = mutex.withLock {
        val checkpoint = local.checkpoint()
        if (checkpoint != null && checkpoint.profile != profile.id)
            throw IOException("Os favoritos deste perfil local já estão associados a outro perfil da Conta Squi TV.")
        if (checkpoint == null && !allowImport)
            throw IOException("Confirme a importação inicial dos favoritos locais.")

        val original = local.snapshot().filter { it.id > 0 }.associateBy(CloudFavorite::key)
        var conflicts = 0
        if (checkpoint == null) {
            for (batch in original.values.chunked(20)) {
                call("favorites", profile.id, body = json.encodeToJsonElement(FavoriteBatch("import", batch)))
            }
        } else {
            val baseline = checkpoint.rows.associateBy(CloudFavorite::key)
            val additions = original.values.filter { baseline[it.key]?.deleted != false }
            val removals = baseline.values.filter { !it.deleted && it.key !in original }
            val changes = additions.map { favorite ->
                favorite.copy(revision = baseline[favorite.key]?.revision ?: 0, deleted = false)
            } + removals.map { it.copy(deleted = true) }
            for (batch in changes.chunked(20)) {
                val reply = call("favorites", profile.id, body = json.encodeToJsonElement(FavoriteBatch("update", batch)))
                conflicts += (reply["conflicts"] as? JsonPrimitive)?.intOrNull?.takeIf { it in 0..batch.size }
                    ?: throw IOException("Resposta de sincronização inválida.")
            }
        }

        val cloud = allFavorites(profile.id)
        if (!local.applyChanges(original.keys, cloud))
            throw IOException("A lista mudou durante a sincronização. Tente novamente.")
        val updated = FavoriteCheckpoint(
            profile.id, cloud.map { it.copy(title = null) },
            checkpoint?.boundAt?.takeIf { it > 0 } ?: System.currentTimeMillis(),
            checkpoint?.categoryBaseline.orEmpty(),
        )
        if (updated != checkpoint) local.saveCheckpoint(updated)
        local.applyProfile(profile)
        conflicts
    }

    suspend fun sendCategories(profile: CloudProfile, local: FavoriteLocalStore) = mutex.withLock {
        val checkpoint = requireBinding(profile, local)
        val revisions = categories(profile.id).associate { it.kind to it.revision }
        val items = local.categorySnapshot().map { it.copy(revision = revisions[it.kind] ?: 0) }
        local.saveCheckpoint(checkpoint.copy(categoryBaseline = writeCategories(profile.id, items)))
    }

    suspend fun receiveCategories(profile: CloudProfile, local: FavoriteLocalStore) = mutex.withLock {
        val checkpoint = requireBinding(profile, local)
        val original = local.categorySnapshot()
        val rows = categories(profile.id)
        if (rows.isEmpty()) throw IOException("O perfil ainda não tem uma organização salva no painel.")
        if (!local.applyCategories(original, rows))
            throw IOException("A organização mudou neste aparelho. Tente novamente.")
        local.saveCheckpoint(checkpoint.copy(categoryBaseline = rows))
    }

    suspend fun reconcileCategories(profile: CloudProfile, local: FavoriteLocalStore): Int = mutex.withLock {
        val checkpoint = requireBinding(profile, local)
        val original = local.categorySnapshot()
        val remote = categories(profile.id).associateBy(CloudCategories::kind)
        val baseline = checkpoint.categoryBaseline.associateBy(CloudCategories::kind).toMutableMap()
        val send = mutableListOf<CloudCategories>()
        val receive = mutableListOf<CloudCategories>()
        var conflicts = 0
        for (row in original) {
            val other = remote[row.kind]
            val previous = baseline[row.kind]
            val localChanged = previous?.let { !sameCategories(row, it) }
                ?: (row.mode != "provider" || row.order.isNotEmpty() || row.hidden.isNotEmpty())
            val remoteChanged = other != null && (previous == null || !sameCategories(other, previous))
            when {
                other != null && sameCategories(row, other) -> baseline[row.kind] = other
                localChanged && remoteChanged -> conflicts++
                localChanged -> send += row.copy(revision = other?.revision ?: 0)
                remoteChanged -> receive += other
            }
        }
        if (send.isNotEmpty()) {
            writeCategories(profile.id, send).forEach { baseline[it.kind] = it }
        }
        if (receive.isNotEmpty()) {
            if (!local.applyCategories(original, receive))
                throw IOException("A organização mudou neste aparelho. Tente novamente.")
            receive.forEach { baseline[it.kind] = it }
        }
        local.saveCheckpoint(checkpoint.copy(categoryBaseline = baseline.values.toList()))
        conflicts
    }

    private fun sameCategories(a: CloudCategories, b: CloudCategories) =
        a.mode == b.mode && a.order == b.order && a.hidden.toSet() == b.hidden.toSet()

    private suspend fun writeCategories(profile: String, rows: List<CloudCategories>): List<CloudCategories> =
        json.decodeFromString<Categories>(
            call("categories", profile, body = json.encodeToJsonElement(Categories(rows))).toString()
        ).items

    suspend fun syncProgress(profile: CloudProfile, local: FavoriteLocalStore): Int = mutex.withLock {
        val checkpoint = requireBinding(profile, local)
        exchangeProgress(checkpoint, local, local.progressJournal().pending)
    }

    suspend fun syncPendingProgress(local: FavoriteLocalStore) = mutex.withLock {
        val checkpoint = local.checkpoint() ?: return@withLock
        val pending = local.progressJournal().pending
        if (pending.isNotEmpty()) exchangeProgress(checkpoint, local, pending)
    }

    private suspend fun exchangeProgress(
        checkpoint: FavoriteCheckpoint,
        local: FavoriteLocalStore,
        pending: List<CloudProgress>,
    ): Int {
        for (batch in pending.chunked(20)) {
            call("progress", checkpoint.profile, body = json.encodeToJsonElement(ProgressBatch(batch)))
        }
        val rows = mutableListOf<CloudProgress>()
        var after = 0L
        repeat(125) {
            val page = json.decodeFromString<ProgressPage>(call("progress", checkpoint.profile, after).toString())
            rows += page.items
            if (!page.hasMore) {
                val merged = rows.associateBy(CloudProgress::key).values.toList()
                if (pending.isNotEmpty() || merged != local.progressJournal().downloaded) {
                    local.applyProgress(checkpoint.boundAt, merged)
                    FavoriteCloudSession.mergeProgress(local, pending, merged)
                }
                return merged.count { !it.deleted }
            }
            if (page.next <= after) throw IOException("O servidor retornou uma revisão inválida.")
            after = page.next
        }
        throw IOException("A lista de progresso excede o limite de sincronização.")
    }

    private suspend fun requireBinding(profile: CloudProfile, local: FavoriteLocalStore): FavoriteCheckpoint {
        val checkpoint = local.checkpoint()
        if (checkpoint?.profile != profile.id)
            throw IOException("Associe o perfil aos favoritos antes de sincronizar os dados.")
        return checkpoint
    }

    private suspend fun categories(profile: String): List<CloudCategories> =
        json.decodeFromString<Categories>(call("categories", profile).toString()).items

    private suspend fun allFavorites(profile: String): List<CloudFavorite> {
        val rows = mutableListOf<CloudFavorite>()
        var after = 0L
        repeat(120) {
            val page = json.decodeFromString<Page>(call("favorites", profile, after).toString())
            rows += page.items
            if (!page.hasMore) return rows.associateBy(CloudFavorite::key).values.toList()
            if (page.next <= after) throw IOException("O servidor retornou uma revisão inválida.")
            after = page.next
        }
        throw IOException("A lista de favoritos excede o limite de sincronização.")
    }

    private suspend fun call(
        endpoint: String,
        profile: String? = null,
        after: Long? = null,
        body: kotlinx.serialization.json.JsonElement? = null,
    ): JsonObject {
        val url = "https://squitv.rafael020703.shop/api/sync/$endpoint".toHttpUrl().newBuilder().apply {
            profile?.let { addQueryParameter("profile", it) }
            after?.let { addQueryParameter("after", it.toString()) }
        }.build()
        val request = Request.Builder().url(url).header("Authorization", "SquiSync " + token)
        if (body != null) request.post(body.toString().toRequestBody("application/json".toMediaType()))
        val (status, response) = AccountPilot.execute(request.build())
        if (status == 200) return response
        if (status == 401) throw IOException("A sessão de sincronização expirou. Entre novamente com a Conta Squi TV.")
        val message = (response["message"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        throw IOException(message ?: "Não foi possível sincronizar a conta (HTTP $status).")
    }
}
