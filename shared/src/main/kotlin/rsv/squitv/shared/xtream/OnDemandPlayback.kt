package rsv.squitv.shared.xtream

import rsv.squitv.data.model.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Same Xtream movie/series endpoints and season-name fallback used by Android. */
object OnDemandPlayback {
    fun address(credentials: ServerCredentials, series: Boolean, id: Int, extension: String?): String {
        require(id > 0)
        val format = extension?.trim()?.trimStart('.')?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,10}")) } ?: "mp4"
        fun segment(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
        return ServerAddress.normalize(credentials.baseUrl) + (if (series) "series/" else "movie/") +
            segment(credentials.username) + "/" + segment(credentials.password) + "/$id.$format"
    }

    fun seasons(info: XtreamSeriesInfo): List<Pair<Int, String>> {
        val known = info.seasons.orEmpty().mapNotNull { s -> s.seasonNumber?.takeIf { it >= 0 }
            ?.let { it to (s.name?.takeIf(String::isNotBlank) ?: "Temporada $it") } }.toMap()
        val inferred = info.episodes.orEmpty().flatMap { (key, episodes) ->
            listOfNotNull(seasonNumber(info, key)) + episodes.mapNotNull { it.season?.takeIf { n -> n >= 0 } }
        }
        // Match Android's series navigation: numbered seasons first, specials (season 0) last.
        return (known.keys + inferred).distinct().sortedWith(compareBy<Int> { it == 0 }.thenBy { it })
            .map { it to (known[it] ?: if (it == 0) "Especiais" else "Temporada $it") }
    }

    fun episodes(info: XtreamSeriesInfo, season: Int): List<XtreamEpisode> {
        return info.episodes.orEmpty().flatMap { (key, episodes) ->
            val belongs = seasonNumber(info, key) == season
            episodes.filter { it.season == season || (it.season == null && belongs) }
        }.filter { (it.id?.toIntOrNull() ?: 0) > 0 }.distinctBy { it.id }
            .sortedWith(compareBy<XtreamEpisode> { it.episodeNum?.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.id?.toIntOrNull() })
    }

    // Resolve bucket keys once by the same rules for discovery AND episode selection.
    // Never infer a season from an arbitrary number embedded in a provider's label.
    private fun seasonNumber(info: XtreamSeriesInfo, key: String): Int? {
        val trimmed = key.trim()
        val named = info.seasons.orEmpty().firstOrNull { it.name?.trim()?.equals(trimmed, true) == true }
            ?.seasonNumber?.takeIf { it >= 0 }
        return named ?: trimmed.toIntOrNull()?.takeIf { it >= 0 }
            ?: seasonLabel.matchEntire(trimmed)?.groupValues?.get(1)?.toIntOrNull()
    }

    private val seasonLabel = Regex("(?:Season|Temporada)[\\s._-]*(\\d+)", RegexOption.IGNORE_CASE)
}
