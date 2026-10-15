package rsv.squitv.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import rsv.squitv.data.local.entities.EpisodeEntity
import rsv.squitv.data.local.entities.SeasonEntity
import rsv.squitv.data.model.XtreamCredentials
import rsv.squitv.domain.model.ContentType
import rsv.squitv.domain.model.IptvItem
import rsv.squitv.domain.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseRepository @Inject constructor() {
    companion object {
        private const val FIREBASE_TIMEOUT = 5000L // 5 segundos
        private const val QUOTA_RETRY_INTERVAL = 1000 * 60 * 15 // 15 minutos
    }

    private var isQuotaExceeded = false
    private var lastQuotaCheck = 0L

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val remoteConfig = FirebaseRemoteConfig.getInstance()

    init {
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(mapOf(
            "min_version" to 1L,
            "latest_version" to 1L,
            "update_url" to ""
        ))
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    private suspend fun getUserId(): String? {
        return try {
            withTimeoutOrNull(FIREBASE_TIMEOUT) {
                val user = auth.currentUser ?: auth.signInAnonymously().await().user
                user?.uid
            }
        } catch (e: Exception) {
            handleFirebaseError(e, "Firebase Authentication failed")
            null
        }
    }

    private fun handleFirebaseError(e: Exception, context: String) {
        val msg = e.message ?: ""
        if (msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) || msg.contains("Quota exceeded", ignoreCase = true)) {
            Timber.w("Firebase Quota Exceeded during $context. Switching to local-only mode for 15 mins.")
            isQuotaExceeded = true
            lastQuotaCheck = System.currentTimeMillis()
        } else {
            Timber.e(e, "Error during $context")
        }
    }

    private fun shouldSkipFirebase(): Boolean {
        if (isQuotaExceeded) {
            val now = System.currentTimeMillis()
            if (now - lastQuotaCheck > QUOTA_RETRY_INTERVAL) {
                isQuotaExceeded = false
                return false
            }
            return true
        }
        return false
    }

    suspend fun addFavorite(profileId: String, item: IptvItem, providerHash: String? = null): Boolean {
        if (shouldSkipFirebase()) return false
        return try {
            val uid = getUserId() ?: return false
            val collection = firestore.collection("users").document(uid)
                .collection("profiles").document(profileId)
                .collection("favorites")
            
            val docId = if (providerHash != null) "${providerHash}_${item.id}" else item.id
            val data = mapOf(
                "id" to item.id,
                "name" to item.name,
                "type" to item.type.toString(),
                "icon" to item.icon,
                "epgId" to item.epgId,
                "category" to item.categoryId,
                "providerHash" to providerHash,
                "timestamp" to System.currentTimeMillis()
            )
            collection.document(docId).set(data, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            handleFirebaseError(e, "adding favorite")
            false
        }
    }

    suspend fun removeFavorite(profileId: String, itemId: String, providerHash: String? = null): Boolean {
        if (shouldSkipFirebase()) return false
        return try {
            val uid = getUserId() ?: return false
            val docId = if (providerHash != null) "${providerHash}_$itemId" else itemId
            firestore.collection("users").document(uid)
                .collection("profiles").document(profileId)
                .collection("favorites").document(docId).delete().await()
            true
        } catch (e: Exception) {
            handleFirebaseError(e, "removing favorite")
            false
        }
    }

    fun getRemoteFavorites(profileId: String, currentProviderHash: String? = null): Flow<List<IptvItem>> = callbackFlow {
        try {
            val uid = getUserId() ?: run {
                trySend(emptyList())
                close()
                return@callbackFlow
            }
            val listener = firestore.collection("users").document(uid)
                .collection("profiles").document(profileId)
                .collection("favorites")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val items = snapshot?.documents?.mapNotNull { doc ->
                        val remoteHash = doc.getString("providerHash")
                        
                        // Security check: Only sync favorites from the same provider
                        // or legacy favorites without hash (for migration)
                        if (currentProviderHash != null && remoteHash != null && remoteHash != currentProviderHash) {
                            return@mapNotNull null
                        }

                        IptvItem(
                            id = doc.getString("id") ?: return@mapNotNull null,
                            name = doc.getString("name") ?: "",
                            type = ContentType.fromString(doc.getString("type")),
                            icon = doc.getString("icon"),
                            epgId = doc.getString("epgId"),
                            categoryId = doc.getString("category")
                        )
                    } ?: emptyList()
                    trySend(items)
                }
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            Timber.e(e, "Error getting favorites from Firebase")
            trySend(emptyList())
            close()
        }
    }

    suspend fun addHistory(profileId: String, item: IptvItem): Boolean {
        if (shouldSkipFirebase()) return false
        return try {
            val uid = getUserId() ?: return false
            val collection = firestore.collection("users").document(uid)
                .collection("profiles").document(profileId)
                .collection("history")
            
            val data = mapOf(
                "id" to item.id,
                "name" to item.name,
                "type" to item.type.toString(),
                "icon" to item.icon,
                "timestamp" to System.currentTimeMillis()
            )
            collection.document(item.id).set(data, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            handleFirebaseError(e, "adding history")
            false
        }
    }

    suspend fun getProfiles(): List<UserProfile> {
        return try {
            val uid = getUserId() ?: return emptyList()
            val snapshot = withTimeoutOrNull(FIREBASE_TIMEOUT) {
                firestore.collection("users").document(uid)
                    .collection("profiles").get().await()
            }
            snapshot?.documents?.mapNotNull { doc ->
                UserProfile(
                    id = doc.id,
                    name = doc.getString("name") ?: "Profile",
                    iconUrl = doc.getString("iconUrl"),
                    isChild = doc.getBoolean("isChild") ?: false,
                    pin = doc.getString("pin")
                )
            } ?: emptyList()
        } catch (e: Exception) {
            handleFirebaseError(e, "getting profiles")
            emptyList()
        }
    }

    suspend fun saveProfile(profile: UserProfile) {
        if (shouldSkipFirebase()) return
        try {
            val uid = getUserId() ?: return
            val docRef = firestore.collection("users").document(uid)
                .collection("profiles").document(profile.id)
            val data = mapOf(
                "id" to profile.id,
                "name" to profile.name,
                "iconUrl" to profile.iconUrl,
                "isChild" to profile.isChild,
                "pin" to profile.pin,
                "lastUpdate" to System.currentTimeMillis()
            )
            withTimeoutOrNull(FIREBASE_TIMEOUT) {
                docRef.set(data, SetOptions.merge()).await()
            }
        } catch (e: Exception) {
            handleFirebaseError(e, "saving profile")
        }
    }

    suspend fun updateProfileIcon(profileId: String, url: String) {
        if (shouldSkipFirebase()) return
        try {
            val uid = getUserId() ?: return
            withTimeoutOrNull(FIREBASE_TIMEOUT) {
                firestore.collection("users").document(uid)
                    .collection("profiles").document(profileId)
                    .update("iconUrl", url).await()
            }
        } catch (e: Exception) {
            handleFirebaseError(e, "updating profile icon")
        }
    }

    suspend fun syncWatchProgress(profileId: String, streamId: String, data: Map<String, Any>): Boolean {
        if (shouldSkipFirebase()) return false
        return try {
            val uid = getUserId() ?: return false
            val docRef = firestore.collection("users").document(uid)
                .collection("profiles").document(profileId)
                .collection("watch_progress").document(streamId)
            
            withTimeoutOrNull(FIREBASE_TIMEOUT) {
                docRef.set(data, SetOptions.merge()).await()
            }
            true
        } catch (e: Exception) {
            handleFirebaseError(e, "syncing watch progress")
            false
        }
    }

    fun getRemoteWatchProgress(profileId: String): Flow<List<Map<String, Any>>> = callbackFlow {
        try {
            val uid = getUserId() ?: run {
                trySend(emptyList())
                close()
                return@callbackFlow
            }
            val listener = firestore.collection("users").document(uid)
                .collection("profiles").document(profileId)
                .collection("watch_progress")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.mapNotNull { it.data?.plus("streamId" to it.id) } ?: emptyList()
                    trySend(list)
                }
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            close(e)
        }
    }

    suspend fun syncAppSettings(settings: SettingsRepository.AppSettings) {
        if (shouldSkipFirebase()) return
        try {
            val uid = getUserId() ?: return
            val docRef = firestore.collection("users").document(uid).collection("settings").document("general")
            val data = mapOf(
                "useOledTheme" to settings.useOledTheme,
                "uiZoom" to settings.uiZoom,
                "language" to settings.language,
                "playerEngine" to settings.playerEngine,
                "bufferStrategy" to settings.bufferStrategy,
                "autoPlayEnabled" to settings.autoPlayEnabled,
                "compactMode" to settings.compactMode,
                "hideBlockedCategories" to settings.hideBlockedCategories,
                "appLockEnabled" to settings.appLockEnabled,
                "appPin" to settings.appPin,
                "lastSync" to System.currentTimeMillis()
            )
            withTimeoutOrNull(FIREBASE_TIMEOUT) {
                docRef.set(data, SetOptions.merge()).await()
            }
        } catch (e: Exception) {
            handleFirebaseError(e, "syncing app settings")
        }
    }

    fun getRemoteSettings(): Flow<Map<String, Any?>> = callbackFlow {
        try {
            val uid = getUserId() ?: run {
                trySend(emptyMap())
                close()
                return@callbackFlow
            }
            val listener = firestore.collection("users").document(uid).collection("settings").document("general")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    trySend(snapshot?.data ?: emptyMap())
                }
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            close(e)
        }
    }

    suspend fun deleteProfile(profileId: String) {
        try {
            val uid = getUserId() ?: return
            firestore.collection("users").document(uid)
                .collection("profiles").document(profileId).delete().await()
        } catch (e: Exception) {
            Timber.e(e, "Error deleting profile")
        }
    }

    suspend fun getNewlyAdded(creds: XtreamCredentials, type: ContentType, limit: Int = 20): List<IptvItem> {
        return try {
            val serverId = creds.baseUrl.hashCode().toString()
            val snapshot = firestore.collection("catalogs").document(serverId)
                .collection(type.toString().lowercase())
                .orderBy("added", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get().await()
            
            snapshot.documents.mapNotNull { doc ->
                IptvItem(
                    id = doc.getString("id") ?: return@mapNotNull null,
                    name = doc.getString("name") ?: "",
                    icon = doc.getString("icon"),
                    type = type,
                    categoryId = doc.getString("categoryId"),
                    added = doc.getString("added"),
                    rating = doc.getString("rating")
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting newly added from Firebase")
            emptyList()
        }
    }

    suspend fun notifyCatalogUpdated(creds: XtreamCredentials, newMovies: List<String>, newSeries: List<String>) {
        try {
            val serverId = creds.baseUrl.hashCode().toString()
            val docRef = firestore.collection("catalogs").document(serverId)
            val data = mapOf(
                "lastUpdate" to System.currentTimeMillis(),
                "newMovieCount" to newMovies.size,
                "newSeriesCount" to newSeries.size,
                "newMovies" to newMovies.take(10), // Limit to 10 names to avoid huge docs
                "newSeries" to newSeries.take(10),
                "serverUrl" to creds.baseUrl,
                "updatedBy" to (auth.currentUser?.uid ?: "unknown")
            )
            docRef.set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Timber.e(e, "Error notifying catalog update to Firebase")
        }
    }

    fun listenToCatalogChanges(creds: XtreamCredentials): Flow<Map<String, Any?>> = callbackFlow {
        try {
            val serverId = creds.baseUrl.hashCode().toString()
            val listener = firestore.collection("catalogs").document(serverId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        trySend(snapshot.data ?: emptyMap())
                    }
                }
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            close(e)
        }
    }

    suspend fun syncSeriesDetails(creds: XtreamCredentials, seriesId: Int, seasons: List<SeasonEntity>, episodes: List<EpisodeEntity>) {
        try {
            val serverId = creds.baseUrl.hashCode().toString()
            val docRef = firestore.collection("catalogs").document(serverId)
                .collection("series_details").document(seriesId.toString())

            val data = mapOf(
                "seriesId" to seriesId,
                "seasons" to seasons.map { mapOf("number" to it.seasonNumber, "name" to it.name, "cover" to it.cover) },
                "episodesCount" to episodes.size,
                "lastSync" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()

            val episodesColl = docRef.collection("episodes")
            episodes.chunked(500).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { ep ->
                    val epDoc = episodesColl.document(ep.streamId.toString())
                    val epData = mapOf(
                        "id" to ep.streamId,
                        "season" to ep.seasonNumber,
                        "episode" to ep.episodeNum,
                        "title" to ep.title,
                        "image" to ep.image,
                        "plot" to ep.plot,
                        "added" to System.currentTimeMillis()
                    )
                    batch.set(epDoc, epData, SetOptions.merge())
                }
                batch.commit().await()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error syncing series details to Firebase")
        }
    }

    suspend fun updateItemMetadata(creds: XtreamCredentials, item: IptvItem) {
        try {
            val serverId = creds.baseUrl.hashCode().toString()
            val typeStr = item.type.toString().lowercase()
            val docRef = firestore.collection("catalogs").document(serverId)
                .collection(typeStr).document(item.id)

            val data = mutableMapOf<String, Any?>(
                "cast" to item.cast,
                "director" to item.director,
                "plot" to item.plot,
                "duration" to item.duration,
                "backdrop" to item.backdropUrl,
                "releaseDate" to item.releaseDate,
                "lastSync" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Timber.e(e, "Error updating item metadata in Firebase")
        }
    }

    suspend fun checkAppUpdate(): AppUpdateInfo {
        return try {
            remoteConfig.fetchAndActivate().await()
            AppUpdateInfo(
                minVersion = remoteConfig.getLong("min_version").toInt(),
                latestVersion = remoteConfig.getLong("latest_version").toInt(),
                updateUrl = remoteConfig.getString("update_url")
            )
        } catch (e: Exception) {
            Timber.e(e, "Error checking app update")
            AppUpdateInfo(1, 1, "")
        }
    }

    suspend fun backupCredentials(deviceId: String, credentials: XtreamCredentials) {
        // Disabled: Credentials are maintained exclusively on local encrypted storage (DataStore)
    }

    suspend fun getBackupCredentials(deviceId: String): XtreamCredentials? {
        // Disabled: Credentials are maintained exclusively on local encrypted storage (DataStore)
        return null
    }

    suspend fun updateProfilePicture(url: String) {
        if (shouldSkipFirebase()) return
        try {
            val uid = getUserId() ?: return
            withTimeoutOrNull(FIREBASE_TIMEOUT) {
                firestore.collection("users").document(uid).set(mapOf("profileImageUrl" to url), SetOptions.merge()).await()
            }
        } catch (e: Exception) {
            handleFirebaseError(e, "updating profile picture")
        }
    }

    fun getProfilePicture(): Flow<String?> = callbackFlow {
        var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
        
        try {
            val uid = getUserId() ?: run {
                trySend(null)
                close()
                return@callbackFlow
            }
            val docRef = firestore.collection("users").document(uid)
            
            listenerRegistration = docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Firestore error in getProfilePicture")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(snapshot.getString("profileImageUrl"))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error initializing profile picture listener")
            trySend(null)
        }
        
        awaitClose { 
            listenerRegistration?.remove() 
        }
    }
}

data class AppUpdateInfo(
    val minVersion: Int,
    val latestVersion: Int,
    val updateUrl: String
)
