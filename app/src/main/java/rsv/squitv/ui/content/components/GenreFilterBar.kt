package rsv.squitv.ui.content.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rsv.squitv.core.ui.components.common.adaptiveFocus
import rsv.squitv.core.ui.theme.AppDesignSystem

@Composable
fun GenreFilterBar(
    genres: List<String>,
    selectedGenre: String?,
    onGenreSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive

    if (genres.isEmpty()) return

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = responsive.dp(tokens.spacing.large)),
        horizontalArrangement = Arrangement.spacedBy(responsive.dp(tokens.spacing.medium)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            GenreChip(
                label = "TODOS",
                isSelected = selectedGenre == null,
                onClick = { onGenreSelect(null) }
            )
        }
        items(genres) { genre ->
            GenreChip(
                label = genre.uppercase(),
                isSelected = selectedGenre.equals(genre, ignoreCase = true),
                onClick = { onGenreSelect(genre) }
            )
        }
    }
}

@Composable
fun GenreChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = AppDesignSystem
    var isFocused by remember { mutableStateOf(false) }

    val textColor by animateColorAsState(
        targetValue = if (isFocused || isSelected) tokens.colors.primary else tokens.colors.textSecondary,
        label = "chipTextColor"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isFocused) tokens.colors.primary.copy(alpha = 0.25f)
                    else if (isSelected) tokens.colors.primary.copy(alpha = 0.12f)
                    else tokens.colors.surfaceVariant.copy(alpha = 0.1f),
        label = "chipBgColor"
    )

    Surface(
        modifier = modifier
            .adaptiveFocus(
                shape = tokens.shapes.small,
                focusedScale = 1.04f,
                onFocus = { isFocused = it }
            )
            .clickable { onClick() },
        shape = tokens.shapes.small,
        color = bgColor
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = textColor,
                style = tokens.typography.caption.copy(fontSize = 11.sp),
                fontWeight = if (isFocused || isSelected) FontWeight.Black else FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}
