package rsv.squitv.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import javax.imageio.ImageIO

/** Rendered by the same buildSrc drawing that generates Android launcher icons. */
@Composable
internal fun brandPainter(): BitmapPainter = remember {
    val resource = checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream("brand/app-icon.png"))
    resource.use { BitmapPainter(ImageIO.read(it).toComposeImageBitmap()) }
}
