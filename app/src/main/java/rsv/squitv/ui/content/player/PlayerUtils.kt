package rsv.squitv.ui.content.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rsv.squitv.R
import rsv.squitv.core.ui.theme.*
import rsv.squitv.data.model.EpgProgramme
import java.util.Locale

@Composable
fun LiveBadge() {
    val tokens = AppDesignSystem
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Surface(
        color = tokens.colors.error.copy(alpha = alpha), 
        shape = tokens.shapes.small
    ) {
        Text(
            text = stringResource(R.string.live_badge).uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = tokens.typography.caption,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun EpgMiniProgress(program: EpgProgramme, isTv: Boolean) {
    val tokens = AppDesignSystem
    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { calculateProgramProgress(program) },
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isTv) 6.dp else 4.dp)
                .clip(CircleShape),
            color = tokens.colors.primary,
            trackColor = tokens.colors.surfaceVariant.copy(alpha = 0.1f)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = tokens.spacing.tiny), 
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
             Text(
                 text = formatProgramTime(program.start),
                 style = tokens.typography.caption, 
                 color = tokens.colors.textSecondary.copy(alpha = 0.5f)
             )
             Text(
                 text = formatProgramTime(program.stop),
                 style = tokens.typography.caption, 
                 color = tokens.colors.textSecondary.copy(alpha = 0.5f)
             )
        }
    }
}


fun calculateProgramProgress(prog: EpgProgramme): Float {
    try {
        val now = System.currentTimeMillis() / 1000
        val start = prog.start.toLongOrNull() ?: return 0f
        val stop = prog.stop.toLongOrNull() ?: return 0f
        if (stop <= start) return 0f
        return ((now - start).toFloat() / (stop - start).toFloat()).coerceIn(0f, 1f)
    } catch (_: Exception) { return 0f }
}

fun formatTime(ms: Long, forceHours: Boolean = false): String {
    val totalSeconds = (ms.coerceAtLeast(0L)) / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0 || forceHours) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

fun formatProgramTime(value: String): String {
    val seconds = value.toLongOrNull()
    return if (seconds != null && seconds > 0L)
        java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(seconds * 1000L))
    else value.substringAfter(' ').take(5)
}
