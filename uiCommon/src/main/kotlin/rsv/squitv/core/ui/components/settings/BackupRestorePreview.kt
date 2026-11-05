package rsv.squitv.core.ui.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Presentation only; each platform validates its own backup format before showing it. */
@Composable
fun BackupRestorePreview(summary: String, description: String, warning: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(summary)
        Text(description)
        warning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
