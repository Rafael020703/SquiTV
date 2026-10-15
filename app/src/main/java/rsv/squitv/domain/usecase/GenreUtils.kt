package rsv.squitv.domain.usecase

import java.util.Locale

/**
 * Domain utility for parsing, normalizing, and standardizing movie and series genres.
 */
object GenreUtils {

    private val junkKeywords = setOf(
        "4k", "hd", "fhd", "sd", "h265", "hevc", "netflix", "prime", "hbo", "disney", "apple", "globoplay",
        "dublado", "legendado", "lancamento", "lancamentos", "2024", "2025", "2026", "atualizados",
        "colecao", "adult", "xxx", "vod", "series", "filmes", "canais", "1080p", "720p", "séries",
        "cinema", "bluray", "complete", "legendas", "subbed", "dual"
    )

    /**
     * Normalizes a raw genre string or token into a standardized human-readable genre name.
     * Returns null if the token is blank, short, or represents a format/category junk keyword.
     */
    fun normalizeGenreToken(token: String): String? {
        val clean = token.trim().lowercase(Locale.ROOT)
        if (clean.isBlank() || clean.length < 3) return null

        // Ignore generic format/playlist keywords that are not true genres
        if (junkKeywords.any { clean == it || clean.startsWith("$it ") || clean.endsWith(" $it") }) {
            return null
        }

        return when {
            clean.contains("action") || clean.contains("ação") || clean.contains("acao") -> "Ação"
            clean.contains("adventure") || clean.contains("aventura") -> "Aventura"
            clean.contains("animat") || clean.contains("anime") || clean.contains("desenho") -> "Animação"
            clean.contains("comed") || clean.contains("comédia") || clean.contains("comedia") -> "Comédia"
            clean.contains("crime") -> "Crime"
            clean.contains("documenta") -> "Documentário"
            clean.contains("drama") -> "Drama"
            clean.contains("famil") || clean.contains("família") -> "Família"
            clean.contains("fanta") -> "Fantasia"
            clean.contains("histor") || clean.contains("histór") -> "História"
            clean.contains("horror") || clean.contains("terror") -> "Terror"
            clean.contains("musi") || clean.contains("músic") -> "Música"
            clean.contains("myster") || clean.contains("mistér") -> "Mistério"
            clean.contains("roman") -> "Romance"
            clean.contains("sci") || clean.contains("ficção") || clean.contains("ficcao") || clean.contains("science") -> "Ficção Científica"
            clean.contains("thrill") || clean.contains("suspen") -> "Suspense"
            clean.contains("war") || clean.contains("guerra") -> "Guerra"
            clean.contains("west") || clean.contains("faroes") -> "Faroeste"
            else -> clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    /**
     * Splits a raw genre string into distinct normalized genre names.
     */
    fun extractNormalizedGenres(rawGenre: String?): List<String> {
        if (rawGenre.isNullOrBlank()) return emptyList()
        return rawGenre.split(",", "/", "|", "&", ";")
            .mapNotNull { normalizeGenreToken(it) }
            .distinct()
    }

    /**
     * Checks if a raw genre string contains a target normalized genre.
     */
    fun matchesGenre(rawGenre: String?, targetGenre: String): Boolean {
        if (rawGenre.isNullOrBlank() || targetGenre.isBlank()) return false
        val itemGenres = extractNormalizedGenres(rawGenre)
        val targetNormalized = normalizeGenreToken(targetGenre) ?: targetGenre.trim()
        return itemGenres.any { it.equals(targetNormalized, ignoreCase = true) }
    }
}
