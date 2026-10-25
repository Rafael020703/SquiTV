package rsv.squitv.domain.usecase

import rsv.squitv.core.data.repository.CatalogRepository
import rsv.squitv.core.data.repository.EpgRepository
import rsv.squitv.core.data.repository.UserRepository
import rsv.squitv.data.model.EpgProgramme
import rsv.squitv.data.model.XtreamStream
import rsv.squitv.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

/**
 * UseCase to handle data for the Quick Switch (Zapping) sidebar.
 * Fetches the list of streams and provides a flow for loading their EPG in background.
 */
class GetQuickSwitchUseCase @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val epgRepository: EpgRepository,
    private val settingsRepository: SettingsRepository,
    private val userRepository: UserRepository
) {
    /**
     * Fetches the list of live streams for a category (or special list, or all if null).
     */
    suspend fun getStreams(categoryId: String?): List<XtreamStream> {
        val credentials = settingsRepository.settingsFlow.first().credentials ?: return emptyList()
        return when {
            categoryId.equals("FAVORITES", ignoreCase = true) -> {
                val favs = userRepository.getFavoritesListFlow().first()
                val liveFavs = favs.filter { it.streamType.equals("LIVE", ignoreCase = true) }
                if (liveFavs.isEmpty()) return emptyList()
                val favIds = liveFavs.map { it.streamId }.distinct()
                val streamEntities = catalogRepository.getStreamsByIds(favIds).associateBy { it.id }
                liveFavs.map { fav ->
                    val dbEntity = streamEntities[fav.streamId]
                    XtreamStream(
                        streamId = fav.streamId,
                        name = fav.name.ifBlank { dbEntity?.name },
                        categoryId = fav.categoryId ?: dbEntity?.categoryId,
                        streamIcon = fav.logo ?: dbEntity?.logo,
                        streamType = fav.streamType,
                        epgChannelId = fav.url ?: dbEntity?.url
                    )
                }
            }
            categoryId.equals("RECENTS", ignoreCase = true) -> {
                val recentItems = catalogRepository.getRecentLiveStreams(limit = 100)
                recentItems.map { item ->
                    XtreamStream(
                        streamId = item.id.toIntOrNull() ?: 0,
                        name = item.name,
                        categoryId = item.categoryId,
                        streamIcon = item.icon,
                        streamType = "LIVE",
                        epgChannelId = item.epgId
                    )
                }
            }
            categoryId.equals("HISTORY", ignoreCase = true) || categoryId.equals("CONTINUE_WATCHING", ignoreCase = true) -> {
                val progressList = userRepository.getAllWatchProgressFlow().first()
                val liveProgress = progressList
                    .filter { it.type.equals("LIVE", ignoreCase = true) }
                    .sortedByDescending { it.lastWatched }
                val streamIds = liveProgress.map { it.streamId }.distinct()
                if (streamIds.isNotEmpty()) {
                    val streamEntities = catalogRepository.getStreamsByIds(streamIds)
                    val entityMap = streamEntities.associateBy { it.id }
                    streamIds.mapNotNull { id -> entityMap[id] }.map { entity ->
                        XtreamStream(
                            streamId = entity.id,
                            name = entity.name,
                            categoryId = entity.categoryId,
                            streamIcon = entity.logo,
                            streamType = entity.streamType,
                            epgChannelId = entity.url
                        )
                    }
                } else {
                    emptyList()
                }
            }
            categoryId != null && categoryId.startsWith("SEARCH:", ignoreCase = true) -> {
                val query = categoryId.substringAfter("SEARCH:")
                if (query.isNotBlank()) {
                    val searchResults = catalogRepository.searchStreams(query, "LIVE")
                    searchResults.map { entity ->
                        XtreamStream(
                            streamId = entity.id,
                            name = entity.name,
                            categoryId = entity.categoryId,
                            streamIcon = entity.logo,
                            streamType = entity.streamType,
                            epgChannelId = entity.url
                        )
                    }
                } else {
                    emptyList()
                }
            }
            categoryId == null || categoryId.equals("ALL", ignoreCase = true) || categoryId.equals("EPG_ALL", ignoreCase = true) -> {
                catalogRepository.getLiveStreams(credentials)
            }
            else -> {
                catalogRepository.getLiveStreams(credentials, categoryId)
            }
        }
    }

    /**
     * Returns a flow that fetches current EPG for the first [limit] streams from local Room DB and emits them.
     */
    fun loadZappingEpg(streams: List<XtreamStream>, limit: Int = 20): Flow<Pair<Int, EpgProgramme>> = flow {
        supervisorScope {
            streams.take(limit).forEach { stream ->
                val streamId = stream.streamId ?: return@forEach
                launch {
                    try {
                        val localPrograms = epgRepository.getEpgForChannel(streamId.toString()).first()
                        val now = System.currentTimeMillis() / 1000
                        val currentLocal = localPrograms.find { it.startTime <= now && it.stopTime > now }
                        
                        if (currentLocal != null) {
                            val prog = EpgProgramme(
                                start = currentLocal.startTime.toString(),
                                stop = currentLocal.stopTime.toString(),
                                channelId = streamId.toString(),
                                title = currentLocal.title,
                                description = currentLocal.description
                            )
                            emit(streamId to prog)
                        }
                    } catch (_: Exception) {
                        // Individual fetch failure, ignore for zapping UI
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
