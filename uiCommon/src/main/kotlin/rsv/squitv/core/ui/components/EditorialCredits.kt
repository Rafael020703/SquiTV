package rsv.squitv.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler

@Composable
fun EditorialCredits() {
    val browser = LocalUriHandler.current
    Column {
        Text("Créditos de metadados", style = MaterialTheme.typography.titleSmall)
        Text("Dados e imagens do TVmaze adaptados para o catálogo.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { browser.openUri("https://www.tvmaze.com/") }) { Text("TVmaze") }
        TextButton(onClick = { browser.openUri("https://creativecommons.org/licenses/by-sa/4.0/") }) { Text("Licença CC BY-SA 4.0") }
        TextButton(onClick = { browser.openUri("https://www.wikidata.org/") }) { Text("Wikidata · CC0") }
    }
}
