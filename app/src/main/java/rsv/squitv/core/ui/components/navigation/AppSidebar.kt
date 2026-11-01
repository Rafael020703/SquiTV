package rsv.squitv.core.ui.components.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rsv.squitv.core.ui.theme.AppDesignSystem
import rsv.squitv.ui.content.components.CategoryListItem

@Composable
fun <T> AppSidebar(
    items: List<T>,
    selectedItemPredicate: (T) -> Boolean,
    itemLabel: @Composable (T) -> String,
    itemCount: ((T) -> Int?)? = null,
    itemKey: ((T) -> String)? = null,
    isHeaderPredicate: ((T) -> Boolean)? = null,
    onItemClick: (T) -> Unit,
    onItemFocus: (T) -> Unit = {},
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    nextFocusRequester: FocusRequester? = null
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive
    val selectedIndex = items.indexOfFirst(selectedItemPredicate)
    
    Surface(
        modifier = modifier
            .width(responsive.dp(tokens.dimensions.sidebarWidth))
            .fillMaxHeight(),
        color = tokens.colors.surface.copy(alpha = 0.2f)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (nextFocusRequester != null) {
                        Modifier.focusProperties { right = nextFocusRequester }
                    } else Modifier
                ),
            verticalArrangement = Arrangement.spacedBy(responsive.dp(tokens.spacing.tiny)),
            contentPadding = PaddingValues(vertical = responsive.dp(tokens.spacing.large), horizontal = responsive.dp(tokens.spacing.small))
        ) {
            itemsIndexed(items, key = itemKey?.let { key -> { _: Int, item: T -> key(item) } }) { index, item ->
                if (isHeaderPredicate?.invoke(item) == true) {
                    Text(
                        text = itemLabel(item).uppercase(),
                        style = tokens.typography.caption.copy(fontSize = responsive.sp(tokens.typography.caption.fontSize)),
                        fontWeight = FontWeight.Black,
                        color = tokens.colors.primary.copy(alpha = 0.8f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = responsive.dp(tokens.spacing.medium),
                                vertical = responsive.dp(tokens.spacing.small)
                            )
                    )
                } else {
                    val isSelected = index == selectedIndex
                    val count = itemCount?.invoke(item)
                    CategoryListItem(
                        name = itemLabel(item),
                        count = count,
                        isSelected = isSelected,
                        modifier = Modifier
                            .then(
                                if (isSelected || (index == 0 && selectedIndex < 0)) {
                                    Modifier.focusRequester(focusRequester)
                                } else Modifier
                            )
                            .then(
                                if (nextFocusRequester != null) {
                                    Modifier.focusProperties { right = nextFocusRequester }
                                } else Modifier
                            ),
                        onClick = { onItemClick(item) },
                        onFocus = { onItemFocus(item) }
                    )
                }
            }
        }
    }
}
