package rsv.squitv.core.ui.components.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rsv.squitv.core.ui.components.common.adaptiveFocus
import rsv.squitv.core.ui.theme.AppDesignSystem

@Composable
fun SettingsGridCard(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive
    var isFocused by remember { mutableStateOf(false) }
    val baseColor = if (isDestructive) tokens.colors.error else tokens.colors.primary

    val cardHeight = responsive.dp(120.dp).coerceAtLeast(102.dp)
    val iconSize = responsive.dp(32.dp).coerceAtLeast(26.dp)
    val fontSize = responsive.sp(11.sp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeight)
            .adaptiveFocus(
                shape = tokens.shapes.extraLarge,
                glowColor = baseColor,
                focusedScale = 1.05f,
                onFocus = { isFocused = it }
            )
            .clickable { onClick() },
        color = if (isFocused) baseColor.copy(alpha = 0.18f) else Color(0xFF0D1322).copy(alpha = 0.82f),
        shape = tokens.shapes.extraLarge,
        border = if (!isFocused) BorderStroke(1.dp, tokens.colors.border.copy(alpha = 0.22f)) else null,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(iconSize + 16.dp)
                    .background(
                        if (isFocused) baseColor.copy(alpha = 0.25f) else tokens.colors.primary.copy(alpha = 0.08f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = if (isFocused) baseColor else tokens.colors.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = label.uppercase(),
                style = tokens.typography.label.copy(fontSize = fontSize),
                fontWeight = FontWeight.Black,
                color = if (isFocused) baseColor else tokens.colors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun SettingsToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive
    var isFocused by remember { mutableStateOf(false) }

    val titleSize = responsive.sp(tokens.typography.title.fontSize)
    val bodySize = responsive.sp(tokens.typography.body.fontSize)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .adaptiveFocus(
                shape = tokens.shapes.medium,
                onFocus = { isFocused = it }
            )
            .clickable { onCheckedChange(!checked) },
        color = if (isFocused) tokens.colors.surfaceElevated else Color.Transparent,
        shape = tokens.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = responsive.dp(tokens.spacing.large), vertical = responsive.dp(tokens.spacing.medium)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label.uppercase(),
                    color = if (isFocused) tokens.colors.primary else tokens.colors.textPrimary,
                    style = tokens.typography.title.copy(fontSize = titleSize),
                    fontWeight = FontWeight.Black
                )
                if (description != null) {
                    Text(
                        text = description,
                        color = tokens.colors.textSecondary,
                        style = tokens.typography.body.copy(fontSize = bodySize)
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = tokens.colors.primary,
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
                    uncheckedBorderColor = Color.Transparent
                ),
                modifier = Modifier.scale(responsive.viewportScale.coerceIn(0.8f, 1.2f))
            )
        }
    }
}

@Composable
fun SettingsActionItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingText: String? = null,
    isDestructive: Boolean = false
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive
    var isFocused by remember { mutableStateOf(false) }
    val color = if (isDestructive) tokens.colors.error else tokens.colors.textPrimary

    val titleSize = responsive.sp(tokens.typography.title.fontSize)
    val labelSize = responsive.sp(tokens.typography.label.fontSize)
    val iconSize = responsive.dp(24.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .adaptiveFocus(
                shape = tokens.shapes.medium,
                onFocus = { isFocused = it }
            )
            .clickable { onClick() },
        color = if (isFocused) tokens.colors.surfaceElevated else Color.Transparent,
        shape = tokens.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = responsive.dp(tokens.spacing.large), vertical = responsive.dp(tokens.spacing.medium)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isFocused) tokens.colors.primary else color,
                    modifier = Modifier.size(iconSize)
                )
                Spacer(modifier = Modifier.width(responsive.dp(tokens.spacing.large)))
            }
            Text(
                text = label.uppercase(),
                color = if (isFocused) tokens.colors.primary else color,
                style = tokens.typography.title.copy(fontSize = titleSize),
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f)
            )
            if (trailingText != null) {
                Text(
                    text = trailingText.uppercase(),
                    color = tokens.colors.primary,
                    style = tokens.typography.label.copy(fontSize = labelSize),
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = tokens.colors.textSecondary.copy(alpha = 0.3f),
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@Composable
fun SettingsStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isHighlight: Boolean = false,
    highlightColor: Color = AppDesignSystem.colors.primary
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive

    val iconSize = responsive.dp(32.dp)
    val valueSize = responsive.sp(tokens.typography.headline.fontSize)
    val labelSize = responsive.sp(tokens.typography.caption.fontSize)

    Surface(
        color = if (isHighlight) highlightColor.copy(alpha = 0.1f) else tokens.colors.surface.copy(alpha = 0.3f),
        shape = tokens.shapes.large,
        border = BorderStroke(responsive.dp(1.dp), if (isHighlight) highlightColor.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(responsive.dp(tokens.spacing.extraLarge)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isHighlight) highlightColor else tokens.colors.textSecondary,
                modifier = Modifier.size(iconSize)
            )
            Spacer(modifier = Modifier.height(responsive.dp(tokens.spacing.medium)))
            Text(
                text = value.uppercase(),
                style = tokens.typography.headline.copy(fontSize = valueSize),
                fontWeight = FontWeight.Black,
                color = if (isHighlight) highlightColor else tokens.colors.textPrimary,
                letterSpacing = responsive.sp(1.sp)
            )
            Text(
                text = label.uppercase(),
                style = tokens.typography.caption.copy(fontSize = labelSize),
                color = tokens.colors.textSecondary,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
        }
    }
}
