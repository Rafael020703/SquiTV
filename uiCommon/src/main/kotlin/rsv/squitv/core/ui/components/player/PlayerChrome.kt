package rsv.squitv.core.ui.components.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.*
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/** Presentation extracted from Android PlayerControlOverlay; native playback stays outside. */
@Composable
fun PlayerTitle(name: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Text(name.uppercase(), modifier, style = style, fontWeight = FontWeight.Black,
        color = color, letterSpacing = 1.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
fun PlayerPauseButton(playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    large: Boolean = false, primary: Color = MaterialTheme.colorScheme.primary) {
    Surface(onClick = onClick, modifier = modifier.size(if (large) 90.dp else 72.dp),
        shape = CircleShape, color = primary, contentColor = Color.Black) {
        Box(contentAlignment = Alignment.Center) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (playing) "Pausar" else "Reproduzir", Modifier.size(if (large) 50.dp else 40.dp))
        }
    }
}

@Composable
fun PlayerActionButton(icon: ImageVector, onClick: () -> Unit, label: String?,
    modifier: Modifier = Modifier, large: Boolean = false, enabled: Boolean = true, tint: Color = Color.White,
    interactionSource: MutableInteractionSource? = null) {
    IconButton(onClick, modifier.size(if (large) 64.dp else 48.dp), enabled = enabled, interactionSource = interactionSource) {
        Icon(icon, label, Modifier.size(if (large) 32.dp else 24.dp), tint = tint.copy(alpha = if (enabled) tint.alpha else .38f))
    }
}

/** Android seek label/badge. Input, buffering and confirmation are platform adapters. */
@Composable
fun PlayerTimeline(positionLabel: String, seeking: Boolean, primary: Color, text: Color,
    labelStyle: TextStyle, captionStyle: TextStyle, modifier: Modifier = Modifier,
    track: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(positionLabel, color = if (seeking) primary else text, style = labelStyle, fontWeight = FontWeight.Bold)
            if (seeking) Surface(color = primary.copy(alpha = .2f), shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, primary)) {
                Text("BUSCANDO...", Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = captionStyle, fontWeight = FontWeight.Black, color = primary)
            }
        }
        Spacer(Modifier.height(4.dp))
        track()
    }
}

@Composable
fun PlayerRoundButton(icon: ImageVector, label: String?, onClick: () -> Unit,
    background: Color, foreground: Color, border: Color, borderWidth: Dp, modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null) {
    Surface(onClick, modifier.size(52.dp), shape = CircleShape, color = background,
        border = BorderStroke(borderWidth, border), interactionSource = interactionSource) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(26.dp), tint = foreground) }
    }
}

@Composable
fun PlayerLiveBadge(label: String, error: Color, style: TextStyle) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(.4f, 1f,
        infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "alpha")
    Surface(color = error.copy(alpha = alpha), shape = RoundedCornerShape(8.dp)) {
        Text(label.uppercase(), Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = style,
            fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 1.sp)
    }
}

@Composable
fun PlayerProgrammeProgress(progress: Float, start: String, end: String, expanded: Boolean,
    primary: Color, surface: Color, secondary: Color, style: TextStyle) {
    Column(Modifier.fillMaxWidth()) {
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(if (expanded) 6.dp else 4.dp).clip(CircleShape),
            color = primary, trackColor = surface.copy(alpha = .1f))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(start, style = style, color = secondary.copy(alpha = .5f))
            Text(end, style = style, color = secondary.copy(alpha = .5f))
        }
    }
}

/** Original LiveBottomOverlay geometry, with slots for platform image loading and real actions. */
@Composable
fun LivePlayerCard(name: String, programme: String, expanded: Boolean,
    background: Color, primary: Color, text: Color, secondary: Color,
    titleStyle: TextStyle, bodyStyle: TextStyle, captionStyle: TextStyle,
    modifier: Modifier = Modifier, badge: @Composable RowScope.() -> Unit,
    logo: @Composable () -> Unit, actions: @Composable RowScope.() -> Unit,
    progress: (@Composable () -> Unit)? = null, upcoming: List<String> = emptyList(),
    extraControls: (@Composable ColumnScope.() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(if (expanded) 20.dp else 12.dp)
        .background(Brush.verticalGradient(listOf(Color.Transparent, background.copy(alpha = .95f))), RoundedCornerShape(16.dp))
        .border(1.dp, Color.White.copy(alpha = .1f), RoundedCornerShape(16.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            logo()
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    badge()
                    Spacer(Modifier.width(8.dp))
                    Text(name.uppercase(), style = titleStyle, color = text, fontWeight = FontWeight.Black,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(programme, style = bodyStyle, color = primary, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), content = actions)
        }
        progress?.let { Spacer(Modifier.height(16.dp)); it() }
        upcoming.take(2).forEach {
            Text(it, style = captionStyle, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        extraControls?.invoke(this)
    }
}

/** Shared VOD control overlay card container matching LivePlayerCard's 16dp rounded border and gradient styling. */
@Composable
fun VodPlayerCard(
    background: Color,
    modifier: Modifier = Modifier,
    timeline: @Composable ColumnScope.() -> Unit,
    actions: @Composable () -> Unit,
    extraControls: (@Composable ColumnScope.() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .background(
                Brush.verticalGradient(listOf(Color.Transparent, background.copy(alpha = 0.95f))),
                RoundedCornerShape(16.dp)
            )
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        timeline()
        actions()
        extraControls?.invoke(this)
    }
}
