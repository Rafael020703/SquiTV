package rsv.squitv.core.ui.components.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import rsv.squitv.core.ui.theme.*

@Composable
fun AppHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = responsive.dp(tokens.spacing.extraLarge), vertical = responsive.dp(tokens.spacing.large)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.uppercase(),
                style = tokens.typography.headline.copy(fontSize = responsive.sp(tokens.typography.headline.fontSize)),
                fontWeight = FontWeight.Black,
                color = tokens.colors.primary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle.uppercase(),
                    style = tokens.typography.caption.copy(fontSize = responsive.sp(tokens.typography.caption.fontSize)),
                    color = tokens.colors.textSecondary
                )
            }
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(responsive.dp(tokens.spacing.medium)),
            content = actions
        )
    }
}
