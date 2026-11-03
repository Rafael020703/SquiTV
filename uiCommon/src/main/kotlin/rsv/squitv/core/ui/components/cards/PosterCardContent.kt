package rsv.squitv.core.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/** Android poster drawing. Platforms supply image loading, interaction and available badge actions. */
@Composable
fun PosterCardContent(name: String, focused: Boolean, primary: Color, text: Color, background: Color,
    titleStyle: TextStyle, titleSize: TextUnit, shape: Shape, aspectRatio: Float,
    spacing: Dp, titlePadding: Dp, progressHeight: Dp, modifier: Modifier = Modifier,
    progress: Float? = null, image: @Composable (Modifier) -> Unit,
    badges: @Composable BoxScope.() -> Unit = {}) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(aspectRatio).clip(shape).background(background)) {
            image(Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)), startY = 0.7f)))
            badges()
            if (progress != null && progress > 0.01f) {
                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(progressHeight),
                    color = primary, trackColor = Color.White.copy(alpha = 0.2f))
            }
        }
        Spacer(Modifier.height(spacing))
        Text(name.uppercase(), color = if (focused) primary else text,
            style = titleStyle.copy(fontSize = titleSize), fontWeight = if (focused) FontWeight.Black else FontWeight.Bold,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = titlePadding))
    }
}
