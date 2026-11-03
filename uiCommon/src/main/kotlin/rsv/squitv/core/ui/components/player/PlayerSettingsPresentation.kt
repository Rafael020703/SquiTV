package rsv.squitv.core.ui.components.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class PlayerSettingsTab(val id: Int, val title: String)

/** Android PlayerSettingsDialog content, independent of Dialog, Media3 and native Windows tracks. */
@Composable
fun PlayerSettingsPanel(tabs: List<PlayerSettingsTab>, selected: Int, opening: Int,
    select: (Int) -> Unit, close: () -> Unit, initialFocus: FocusRequester,
    primary: Color, surface: Color, text: Color, secondary: Color, border: Color,
    modifier: Modifier = Modifier, speedOnly: Boolean = false, heightFraction: Float = 1f,
    options: @Composable () -> Unit) {
    val tabsState = rememberLazyListState(tabs.indexOfFirst { it.id == opening }.coerceAtLeast(0))
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val wide = maxWidth >= 600.dp && !speedOnly
        Surface(Modifier.widthIn(max = if (speedOnly) 420.dp else 760.dp).fillMaxWidth().fillMaxHeight(heightFraction),
            shape = RoundedCornerShape(20.dp), color = surface, border = BorderStroke(1.dp, border.copy(alpha = .5f))) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (speedOnly) "Velocidade" else "Configurações do player",
                            style = MaterialTheme.typography.titleLarge, color = text, fontWeight = FontWeight.SemiBold)
                        Text("Ajustes para a reprodução", style = MaterialTheme.typography.bodySmall, color = secondary)
                    }
                    IconButton(close) { Icon(Icons.Rounded.Close, "Fechar configurações", tint = secondary) }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = border.copy(alpha = .4f))
                Spacer(Modifier.height(12.dp))
                val tabContent: @Composable (PlayerSettingsTab) -> Unit = { tab ->
                    var focused by remember { mutableStateOf(false) }
                    val active = selected == tab.id
                    OutlinedButton({ select(tab.id) }, modifier = (if (wide || speedOnly) Modifier.fillMaxWidth() else Modifier)
                        .then(if (tab.id == opening) Modifier.focusRequester(initialFocus) else Modifier)
                        .onFocusChanged { focused = it.isFocused }, shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (active || focused) primary.copy(alpha = .14f) else Color.Transparent,
                            contentColor = if (active || focused) primary else secondary),
                        border = BorderStroke(if (focused) 2.dp else 1.dp, if (active || focused) primary else border)) {
                        Text(tab.title, style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (wide) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    LazyColumn(Modifier.width(164.dp), state = tabsState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(tabs, key = { it.id }) { tabContent(it) }
                    }
                    VerticalDivider(color = border.copy(alpha = .4f))
                    Box(Modifier.weight(1f)) { options() }
                } else Column(Modifier.weight(1f)) {
                    if (!speedOnly) {
                        LazyRow(state = tabsState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(tabs, key = { it.id }) { tabContent(it) }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    Box(Modifier.weight(1f)) { options() }
                }
            }
        }
    }
    LaunchedEffect(Unit) { withFrameNanos { }; initialFocus.requestFocus() }
}

@Composable
fun PlayerSettingsOption(label: String, sublabel: String? = null, selected: Boolean,
    primary: Color, text: Color, secondary: Color, modifier: Modifier = Modifier,
    enabled: Boolean = true, action: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
        .selectable(selected, enabled = enabled, role = if (action) Role.Button else Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (focused) primary.copy(alpha = .2f) else if (selected) primary.copy(alpha = .08f) else Color.White.copy(alpha = .035f),
        border = BorderStroke(1.dp, if (focused) primary else Color.Transparent)) {
        Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = text.copy(alpha = if (enabled) 1f else .45f))
                if (!sublabel.isNullOrBlank()) Text(sublabel, style = MaterialTheme.typography.bodySmall, color = secondary)
            }
            if (selected) Icon(Icons.Rounded.Check, "Selecionado", tint = primary, modifier = Modifier.size(20.dp))
        }
    }
}
