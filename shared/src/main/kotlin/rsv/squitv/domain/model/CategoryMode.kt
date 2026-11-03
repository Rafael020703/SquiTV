package rsv.squitv.domain.model

import java.util.Locale

/**
 * Category display modes for Movie and Series screens.
 * PROVIDER uses server categories; GENRE uses content metadata genres.
 */
enum class CategoryMode {
    PROVIDER,
    GENRE;

    companion object {
        fun fromString(value: String?): CategoryMode = when (value?.trim()?.uppercase(Locale.ROOT)) {
            "GENRE" -> GENRE
            else -> PROVIDER
        }
    }
}
