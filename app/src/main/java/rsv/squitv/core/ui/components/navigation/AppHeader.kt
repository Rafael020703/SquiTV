package rsv.squitv.core.ui.components.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import rsv.squitv.R
import rsv.squitv.core.ui.components.buttons.AppIconButton
import rsv.squitv.core.ui.theme.*

@Composable
fun AppHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    showBackButton: Boolean = false,
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
        if (showBackButton && onBack != null) {
            AppIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                onClick = onBack,
                contentDescription = stringResource(R.string.back_content_desc),
                modifier = Modifier.padding(end = responsive.dp(tokens.spacing.large))
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            if (title.isNotBlank()) {
                Text(
                    text = title.uppercase(),
                    style = tokens.typography.headline.copy(fontSize = responsive.sp(tokens.typography.headline.fontSize)),
                    fontWeight = FontWeight.Black,
                    color = tokens.colors.primary
                )
            }
            if (!subtitle.isNullOrBlank()) {
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
