package rsv.squitv.shared.account

enum class ProfileAvatar(val key: String, val symbol: String, val label: String, val background: Long) {
    DEFAULT("default", "●", "Padrão", 0xFF273852),
    FOX("fox", "🦊", "Raposa", 0xFF663827),
    CAT("cat", "🐱", "Gato", 0xFF524366),
    PANDA("panda", "🐼", "Panda", 0xFF334941),
    OWL("owl", "🦉", "Coruja", 0xFF594331),
    ROCKET("rocket", "🚀", "Foguete", 0xFF314D72),
    PLANET("planet", "🪐", "Planeta", 0xFF603D69),
    WAVE("wave", "🌊", "Onda", 0xFF255163);

    companion object {
        fun animalPhotoName(key: String?): String? =
            key?.takeIf { it in setOf("fox", "cat", "panda", "owl") }?.let { "$it.jpg" }
        fun fromKey(key: String?): ProfileAvatar = entries.firstOrNull { it.key == key } ?: DEFAULT

        fun photoBytes(key: String?): ByteArray? {
            val prefix = "data:image/png;base64,"
            if (key == null || key.length > 3400 || !key.startsWith(prefix)) return null
            val bytes = try { java.util.Base64.getDecoder().decode(key.substring(prefix.length)) }
            catch (_: IllegalArgumentException) { return null }
            val pngHeader = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82)
            if (bytes.size < 33 || !bytes.copyOfRange(0, pngHeader.size).contentEquals(pngHeader)) return null
            val dimensions = java.nio.ByteBuffer.wrap(bytes, 16, 8)
            val width = dimensions.int
            val height = dimensions.int
            return bytes.takeIf { width in 1..64 && width == height }
        }
    }
}
