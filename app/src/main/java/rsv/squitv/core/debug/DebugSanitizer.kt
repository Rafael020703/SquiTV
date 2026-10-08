package rsv.squitv.core.debug

object DebugSanitizer {
    private val passwordRegex = Regex("(?i)(password|pass|passwd)=[^&\\s]*")
    private val usernameRegex = Regex("(?i)(username|user)=[^&\\s]*")
    private val tokenRegex = Regex("(?i)(token|auth_token|access_token)=[^&\\s]*")
    private val secretKeyRegex = Regex("(?i)(secret|api_key|key)=[^&\\s]*")
    
    // Live stream URL pattern: /live/username/password/streamId.m3u8 or /movie/user/pass/id.mp4
    private val liveStreamUrlRegex = Regex("(?i)/(live|movie|series)/([^/]+)/([^/]+)/")

    fun sanitizeUrl(url: String?): String {
        if (url == null) return ""
        var sanitized = url
            .replace(passwordRegex, "$1=[REDACTED]")
            .replace(usernameRegex, "$1=[REDACTED]")
            .replace(tokenRegex, "$1=[REDACTED]")
            .replace(secretKeyRegex, "$1=[REDACTED]")

        sanitized = liveStreamUrlRegex.replace(sanitized, "/$1/[REDACTED]/[REDACTED]/")
        return sanitized
    }

    fun sanitizeHeader(headerName: String, headerValue: String): String {
        val nameLower = headerName.lowercase()
        return if (nameLower.contains("authorization") || nameLower.contains("cookie") || nameLower.contains("token") || nameLower.contains("secret")) {
            "[REDACTED]"
        } else {
            headerValue
        }
    }

    fun sanitizeText(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text
            .replace(passwordRegex, "$1=[REDACTED]")
            .replace(usernameRegex, "$1=[REDACTED]")
            .replace(tokenRegex, "$1=[REDACTED]")
            .replace(secretKeyRegex, "$1=[REDACTED]")
            .let { liveStreamUrlRegex.replace(it, "/$1/[REDACTED]/[REDACTED]/") }
    }
}
