package rsv.squitv.domain.usecase

import rsv.squitv.core.data.repository.CatalogRepository
import rsv.squitv.core.data.repository.EpgRepository
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
    private val settingsRepository: SettingsRepository
) {
    /**
     * Fetches the list of live streams for a category (or all if null).
     */
    suspend fun getStreams(categoryId: String?): List<XtreamStream> {
        val credentials = settingsRepository.settingsFlow.first().credentials ?: return emptyList()
        return if (categoryId != null) {
            catalogRepository.getLiveStreams(credentials, categoryId)
        } else {
            catalogRepository.getLiveStreams(credentials)
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
