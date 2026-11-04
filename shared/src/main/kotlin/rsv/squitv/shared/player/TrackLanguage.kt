package rsv.squitv.shared.player

import java.text.Normalizer
import java.util.Locale

/** Persist a language, never a media-specific track number. Unknown/ambiguous labels stay local. */
object TrackLanguage {
    private val languages = Locale.getISOLanguages().toSet()
    private val iso3 by lazy { languages.associateBy { runCatching { Locale.forLanguageTag(it).isO3Language }.getOrDefault(it) } }
    fun normalize(value: String?): String? {
        val code = value?.trim()?.lowercase(Locale.ROOT)?.substringBefore('-')?.substringBefore('_') ?: return null
        return code.takeIf { it in languages } ?: iso3[code]
    }
    private fun clean(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    private val names by lazy {
        languages.flatMap { code -> listOf(Locale.ENGLISH, Locale.forLanguageTag("pt-BR"), Locale.forLanguageTag(code)).map { locale ->
            clean(Locale.forLanguageTag(code).getDisplayLanguage(locale)) to code
        } }.filter { it.first.length > 3 }.groupBy({ it.first }, { it.second })
    }
    fun fromLabel(label: String): String? {
        normalize(label)?.let { return it }
        val text = clean(label.take(512))
        val matches = names.filterKeys { Regex("(?<![\\p{L}])${Regex.escape(it)}(?![\\p{L}])").containsMatchIn(text) }
            .values.flatten().toMutableSet()
        Regex("[\\[(]([a-z]{2,3}(?:[-_][a-z]{2,8})?)[\\])]").findAll(text)
            .mapNotNull { normalize(it.groupValues[1]) }.forEach(matches::add)
        return matches.singleOrNull()
    }
}
