package rsv.squitv.shared.player

/** FIT keeps the image; FILL stretches it; ZOOM fills by cropping, without distortion. */
enum class VideoScaleMode(val media3Value: Int) {
    FIT(0), FILL(3), ZOOM(4);
    fun next() = entries[(ordinal + 1) % entries.size]
    companion object { fun fromMedia3(value: Int) = entries.firstOrNull { it.media3Value == value } ?: FIT }
}

data class VlcVideoGeometry(val aspect: String?, val crop: String?)
fun VideoScaleMode.vlcGeometry(width: Int, height: Int): VlcVideoGeometry {
    if (width <= 0 || height <= 0 || this == VideoScaleMode.FIT) return VlcVideoGeometry(null, null)
    var a = width; var b = height
    while (b != 0) { val next = a % b; a = b; b = next }
    val ratio = "${width / a}:${height / a}"
    return if (this == VideoScaleMode.FILL) VlcVideoGeometry(ratio, null) else VlcVideoGeometry(null, ratio)
}
