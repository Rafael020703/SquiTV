package rsv.squitv.shared.brand

/** The Android 1280x720 fit rule, also used by the Windows dashboard. */
object ViewportSizing {
    fun viewportScale(width: Float, height: Float): Float =
        minOf(width / 1280f, height / 720f).coerceIn(.25f, 2f)
    fun textScale(viewport: Float, fontScale: Float): Float =
        (viewport * 1.1f).coerceIn(.65f, 1.4f) * fontScale
}
