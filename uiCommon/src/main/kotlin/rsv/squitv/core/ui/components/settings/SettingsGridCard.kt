package rsv.squitv.core.ui.components.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Shared grid item drawing for settings screen options across Android and Windows. */
@Composable
fun CommonSettingsGridCard(
    label: String,
    icon: ImageVector,
    focused: Boolean,
    baseColor: Color,
    surfaceColor: Color,
    textColor: Color,
    labelStyle: TextStyle,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    cardHeight: Dp = 120.dp,
    iconSize: Dp = 32.dp,
    fontSize: TextUnit = 11.sp,
    shape: Shape = RoundedCornerShape(24.dp),
    borderColor: Color = baseColor.copy(alpha = .15f),
    focusedBorder: Boolean = true,
    idleIconBackground: Color = baseColor.copy(alpha = .08f),
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeight)
            .clickable(
                interactionSource = interactionSource ?: remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        color = if (focused) baseColor.copy(alpha = 0.18f) else surfaceColor,
        shape = shape,
        border = if (focused && !focusedBorder) null else BorderStroke(if (focused) 2.dp else 1.dp, if (focused) baseColor else borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(iconSize + 16.dp)
                    .background(
                        if (focused) baseColor.copy(alpha = 0.25f) else idleIconBackground,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = if (focused) baseColor else textColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = label.uppercase(),
                style = labelStyle.copy(fontSize = fontSize),
                fontWeight = FontWeight.Black,
                color = if (focused) baseColor else textColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = 1.sp
            )
        }
    }
}
