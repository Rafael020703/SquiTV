package rsv.squitv.domain.usecase

import rsv.squitv.core.data.repository.EpgRepository
import rsv.squitv.data.model.EpgListing
import rsv.squitv.data.model.EpgProgramme
import rsv.squitv.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * UseCase to fetch and process EPG for a specific stream.
 * Returns the current program, next upcoming programs, and the full list of listings.
 */
class GetPlayerEpgUseCase @Inject constructor(
    private val epgRepository: EpgRepository,
    private val settingsRepository: SettingsRepository
) {
    data class Result(
        val current: EpgProgramme?,
        val next: List<EpgProgramme>,
        val allListings: List<EpgListing>
    )

    suspend operator fun invoke(streamId: Int): Result {
        val nowTimestamp = System.currentTimeMillis() / 1000

        // 1. Check local Room DB first (fast & zero network traffic to Xtream server)
        try {
            val localPrograms = epgRepository.getEpgForChannel(streamId.toString()).first()
            if (localPrograms.isNotEmpty()) {
                val currentEntity = localPrograms.find { it.startTime <= nowTimestamp && it.stopTime > nowTimestamp }
                val nextEntities = localPrograms.filter { it.startTime > nowTimestamp }.sortedBy { it.startTime }.take(5)
                
                val currentProg = currentEntity?.let {
                    EpgProgramme(it.startTime.toString(), it.stopTime.toString(), streamId.toString(), it.title, it.description)
                }
                val nextProgs = nextEntities.map {
                    EpgProgramme(it.startTime.toString(), it.stopTime.toString(), streamId.toString(), it.title, it.description)
                }
                val listings = localPrograms.map {
                    EpgListing("${it.channelId}_${it.startTime}", it.channelId, it.title, null, null, null, it.description, it.channelId, it.startTime, it.stopTime)
                }
                return Result(currentProg, nextProgs, listings)
            }
        } catch (_: Exception) {}

        // 2. Fallback to remote Xtream API if local Room DB has no EPG entries
        val credentials = settingsRepository.settingsFlow.first().credentials 
            ?: return Result(null, emptyList(), emptyList())

        val response = epgRepository.getShortEpg(credentials, streamId)
        val listings = response.epgListings ?: emptyList()

        val current = listings.find { prog ->
            val start = prog.startTimestamp ?: 0L
            val stop = prog.stopTimestamp ?: 0L
            start != 0L && stop != 0L && nowTimestamp >= start && nowTimestamp < stop
        }?.let {
            EpgProgramme(it.start ?: "", it.end ?: "", streamId.toString(), it.title, it.description)
        }

        val next = listings.filter { (it.startTimestamp ?: 0L) > nowTimestamp }
            .sortedBy { it.startTimestamp }
            .take(5)
            .map {
                EpgProgramme(it.start ?: "", it.end ?: "", streamId.toString(), it.title, it.description)
            }

        return Result(current, next, listings)
    }
}
