package rsv.squitv.shared.downloads

import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

/** Formatting originally used by Android DownloadsScreen. */
object DownloadPresentation {
    fun fileSize(bytes: Long, locale: Locale = Locale.getDefault()): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val group = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(units.indices)
        return String.format(locale, "%.1f %s", bytes / 1024.0.pow(group.toDouble()), units[group])
    }
}
