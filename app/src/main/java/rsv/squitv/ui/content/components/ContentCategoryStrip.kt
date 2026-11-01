package rsv.squitv.ui.content.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rsv.squitv.core.ui.components.common.adaptiveFocus
import rsv.squitv.domain.model.ContentCategory

@Composable
fun ContentCategoryStrip(categories: List<ContentCategory>, selectedId: String?, onSelect: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories, key = { it.id }) { category ->
            if (category.isHeader) {
                Text(
                    text = category.name.uppercase(),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                )
            } else {
                FilterChip(
                    selected = category.id == selectedId,
                    onClick = { onSelect(category.id) },
                    label = { Text("${category.name} (${category.count})") },
                    modifier = Modifier.heightIn(min = 48.dp).adaptiveFocus()
                )
            }
        }
    }
}
