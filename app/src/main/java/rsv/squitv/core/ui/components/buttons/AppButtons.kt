package rsv.squitv.core.ui.components.buttons

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import rsv.squitv.core.ui.theme.*
import rsv.squitv.core.ui.components.common.adaptiveFocus

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    containerColor: Color? = null,
    contentColor: Color? = null,
    useGradient: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val tokens = AppDesignSystem
    val responsive = tokens.responsive
    
    val baseContainerColor = containerColor ?: tokens.colors.primary
    val baseContentColor = contentColor ?: tokens.colors.background
    
    val animatedContainerColor by animateColorAsState(
        if (isFocused) baseContainerColor else baseContainerColor.copy(alpha = 0.88f),
        label = "btnContainer"
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .height(responsive.dp(tokens.dimensions.minTouchTarget).coerceAtLeast(tokens.dimensions.minTouchTarget))
            .adaptiveFocus(
                shape = tokens.shapes.button,
                glowColor = tokens.colors.primary,
                focusedScale = 1.03f,
                onFocus = { isFocused = it }
            )
            .then(
                if (useGradient && enabled) {
                    Modifier
                        .shadow(
                            elevation = if (isFocused) 16.dp else 8.dp,
                            shape = tokens.shapes.button,
                            ambientColor = tokens.colors.primary.copy(alpha = 0.35f),
                            spotColor = tokens.colors.primary
                        )
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    tokens.colors.primary,
                                    PrimaryCyanVariant,
                                    SecondaryPurple
                                )
                            ),
                            shape = tokens.shapes.button
                        )
                } else Modifier
            ),
        enabled = enabled,
        shape = tokens.shapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (useGradient && enabled) Color.Transparent else animatedContainerColor,
            contentColor = baseContentColor,
            disabledContainerColor = tokens.colors.surfaceVariant.copy(alpha = 0.12f),
            disabledContentColor = tokens.colors.textDisabled
        ),
        contentPadding = PaddingValues(horizontal = responsive.dp(tokens.spacing.large))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(responsive.dp(20.dp)))
                Spacer(Modifier.width(responsive.dp(tokens.spacing.small)))
            }
            Text(
                text = text.uppercase(), 
                style = tokens.typography.label.copy(fontSize = responsive.sp(tokens.typography.label.fontSize)),
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun AppSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val tokens = AppDesignSystem
    AppButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        enabled = enabled,
        containerColor = tokens.colors.surfaceVariant.copy(alpha = 0.2f),
        contentColor = tokens.colors.textPrimary
    )
}

@Composable
fun AppIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val tokens = AppDesignSystem
    val responsive = tokens.responsive
    
    val baseTint = tint ?: tokens.colors.textPrimary
    val size = responsive.dp(tokens.dimensions.minTouchTarget + tokens.spacing.small).coerceAtLeast(tokens.dimensions.minTouchTarget)
    
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .adaptiveFocus(
                shape = CircleShape,
                onFocus = { isFocused = it }
            )
            .background(if (isFocused) tokens.colors.textPrimary.copy(alpha = 0.1f) else Color.Transparent, CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) tokens.colors.primary else baseTint,
            modifier = Modifier.size(responsive.dp(24.dp).coerceAtLeast(20.dp))
        )
    }
}
