package rsv.squitv.core.ui.components.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** ChannelListItem's original drawing. Platform adapters supply focus/click and animated colors. */
@Composable
fun PlayerChannelRow(
    name: String,
    selected: Boolean,
    focused: Boolean,
    primary: Color,
    text: Color,
    background: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    logoContent: (@Composable () -> Unit)? = null
) {
    Box(modifier = modifier.fillMaxWidth().background(background).padding(vertical = if (logoContent == null) 16.dp else 12.dp, horizontal = if (logoContent == null) 24.dp else 20.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (logoContent != null) {
                Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    logoContent()
                }
            }
            Text(
                text = name.uppercase(),
                color = text,
                style = style,
                fontWeight = if (selected || focused) FontWeight.Black else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        if (selected && !focused) {
            Box(Modifier.align(Alignment.CenterStart).width(4.dp).height(24.dp).background(primary))
        }
    }
}

/** Text section extracted from Android EpisodeCard. No metadata fetching or playback state. */
@Composable
fun PlayerEpisodeDescription(title: String, plot: String, watched: Boolean, text: Color, secondary: Color,
    titleStyle: TextStyle, bodyStyle: TextStyle, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title.uppercase(), style = titleStyle, color = if (watched) secondary.copy(alpha = .5f) else text,
            fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(plot, style = bodyStyle, color = secondary, maxLines = 2,
            overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
    }
}

@Composable
fun PlayerEpisodeNumber(label: String, primary: Color, style: TextStyle, modifier: Modifier = Modifier,
    foreground: Color = Color.Black) {
    Surface(modifier, color = primary, shape = RoundedCornerShape(bottomEnd = 8.dp)) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = style,
            fontWeight = FontWeight.Black, color = foreground)
    }
}

/** Episode item drawing for player panels and detail cards. */
@Composable
fun PlayerEpisodeRow(
    episodeNum: String,
    title: String,
    plot: String?,
    selected: Boolean,
    focused: Boolean,
    primary: Color,
    text: Color,
    secondary: Color,
    background: Color,
    titleStyle: TextStyle,
    bodyStyle: TextStyle,
    tagStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = background,
        shape = RoundedCornerShape(12.dp),
        border = if (focused) BorderStroke(2.dp, primary) else if (selected) BorderStroke(1.dp, primary.copy(alpha = 0.4f)) else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerEpisodeNumber(
                label = "E$episodeNum",
                primary = if (focused) Color.Black else primary,
                style = tagStyle,
                foreground = if (focused) primary else Color.Black
            )
            PlayerEpisodeDescription(
                title = title,
                plot = plot ?: "",
                watched = false,
                text = text,
                secondary = if (focused) Color.Black.copy(alpha = .72f) else secondary,
                titleStyle = titleStyle,
                bodyStyle = bodyStyle,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
