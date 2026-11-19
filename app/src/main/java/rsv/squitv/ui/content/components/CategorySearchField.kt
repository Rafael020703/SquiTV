package rsv.squitv.ui.content.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import rsv.squitv.R

@Composable
internal fun CategorySearchField(query: String, onQueryChanged: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
        label = { Text(stringResource(R.string.search_in_category)) },
        singleLine = true,
    )
    LaunchedEffect(focus) { focus.requestFocus() }
}
