package rsv.squitv.shared.account

/** Display-name fallback from Android's account screen and dashboard. */
object AccountPresentation {
    fun displayName(customName: String?, fallback: String): String =
        customName?.takeIf { it.isNotBlank() } ?: fallback
}
