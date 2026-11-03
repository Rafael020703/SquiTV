package rsv.squitv.core.ui.components.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import rsv.squitv.core.ui.theme.*
import rsv.squitv.core.ui.components.common.PageHeadingText

/** Page heading and actions; back navigation is handled by Android and the remote. */
@Composable
fun AppHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
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
        PageHeadingText(title, subtitle, tokens.colors.primary, tokens.colors.textSecondary,
            tokens.typography.headline.copy(fontSize = responsive.sp(tokens.typography.headline.fontSize)),
            tokens.typography.caption.copy(fontSize = responsive.sp(tokens.typography.caption.fontSize)),
            Modifier.weight(1f))
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(responsive.dp(tokens.spacing.medium)),
            content = actions
        )
    }
}
