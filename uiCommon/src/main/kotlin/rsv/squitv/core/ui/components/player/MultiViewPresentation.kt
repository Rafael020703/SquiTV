package rsv.squitv.core.ui.components.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.FilterChip
import rsv.squitv.shared.player.VideoScaleMode
import rsv.squitv.shared.catalog.MultiViewPolicy

fun VideoScaleMode.displayName() = when (this) {
    VideoScaleMode.FIT -> "Ajustar"; VideoScaleMode.FILL -> "Preencher"; VideoScaleMode.ZOOM -> "Zoom"
}
fun VideoScaleMode.description() = when (this) {
    VideoScaleMode.FIT -> "Imagem inteira, mantendo a proporção"
    VideoScaleMode.FILL -> "Ocupa toda a área, podendo esticar a imagem"
    VideoScaleMode.ZOOM -> "Ocupa toda a área, cortando as bordas sem esticar"
}
@Composable
fun VideoScaleChoices(mode: VideoScaleMode, choose: (VideoScaleMode) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        VideoScaleMode.entries.forEach { value -> FilterChip(mode == value, { choose(value) }, { Text(value.displayName()) }) }
    }
}

/** Two bounded rows; slot aspect ratios never force scrolling or overflow. */
@Composable
fun MultiViewGrid(modifier: Modifier = Modifier, slot: @Composable (Int, Modifier) -> Unit) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(MultiViewPolicy.SLOT_COUNT / MultiViewPolicy.COLUMNS) { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(MultiViewPolicy.COLUMNS) { column ->
                    slot(row * MultiViewPolicy.COLUMNS + column, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
fun MultiViewSlotSurface(focused: Boolean, background: Color, primary: Color, shape: Shape,
    modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier, shape, background, border = BorderStroke(if (focused) 2.dp else 1.dp,
        if (focused) primary else Color.White.copy(alpha = .05f)), content = content)
}

/** Empty-slot artwork extracted from the Android PlayerSlot. */
@Composable
fun MultiViewEmptyContent(focused: Boolean, primary: Color, text: Color, secondary: Color,
    labelStyle: TextStyle, label: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.AddCircle, null, Modifier.size(56.dp),
            tint = if (focused) primary else secondary.copy(alpha = .2f))
        Spacer(Modifier.height(16.dp))
        Text(label, color = if (focused) text else secondary.copy(alpha = .3f),
            style = labelStyle, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    }
}
