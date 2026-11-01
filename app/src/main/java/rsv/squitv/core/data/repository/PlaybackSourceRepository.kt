package rsv.squitv.core.data.repository

import rsv.squitv.domain.model.IptvItem
import rsv.squitv.domain.model.LiveChannelGrouping
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A snapshot of exactly the visible source list, including its order and membership. */
@Singleton
class PlaybackSourceRepository @Inject constructor() {
    companion object {
        fun scope(settings: rsv.squitv.data.repository.SettingsRepository.AppSettings): String =
            settings.credentials?.let { rsv.squitv.data.model.SettingsBackupCodec.accountKey(it) }.orEmpty() +
                "/" + settings.activeProfileId
    }

    data class Source(val scope: String, val items: List<IptvItem>)
    private val sources = linkedMapOf<String, Source>()

    @Synchronized
    fun capture(scope: String, visibleItems: List<IptvItem>): String {
        val id = UUID.randomUUID().toString()
        sources[id] = Source(scope, LiveChannelGrouping.expand(visibleItems))
        while (sources.size > 8) sources.remove(sources.keys.first())
        return id
    }

    @Synchronized
    fun get(id: String, scope: String): List<IptvItem>? =
        sources[id]?.takeIf { it.scope == scope }?.items
}
