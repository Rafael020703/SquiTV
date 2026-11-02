package rsv.squitv.ui.updates

/** Plain readable release notes; technical download/checksum sections stay on GitHub. */
fun releaseHighlights(markdown: String): List<String> {
    var excludedLevel: Int? = null
    var inCode = false
    return buildList {
        for (raw in markdown.take(40_000).lines()) {
            val line = raw.trim()
            if (line.startsWith("```")) { inCode = !inCode; continue }
            if (inCode || line.isBlank() || line.matches(Regex("[-_*]{3,}"))) continue
            val heading = Regex("^(#{1,6})\\s+(.+)").find(line)
            if (heading != null) {
                val level = heading.groupValues[1].length
                val title = heading.groupValues[2].lowercase()
                if (excludedLevel?.let { level <= it } == true) excludedLevel = null
                if (title.startsWith("download") || title.startsWith("verifica") || title.startsWith("checks") ||
                    title.startsWith("sha-256") || title.startsWith("assets") || title.startsWith("arquivos")) {
                    excludedLevel = level
                }
                continue
            }
            if (excludedLevel != null) continue
            val text = line.replace(Regex("!?\\[([^]]*)]\\([^)]+\\)"), "$1")
                .replace(Regex("^[-*+]\\s+|^\\d+[.)]\\s+|^>\\s*"), "")
                .replace("**", "").replace("__", "").replace("`", "").trim()
            if (text.isNotBlank() && !text.matches(Regex("[a-fA-F0-9]{64}"))) add(text)
            if (size >= 60) break
        }
    }
}
