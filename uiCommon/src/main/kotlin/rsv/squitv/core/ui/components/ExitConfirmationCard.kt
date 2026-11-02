package rsv.squitv.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Android exit confirmation presentation; the host owns modality and application shutdown. */
@Composable
fun ExitConfirmationCard(title: String, message: String, exitLabel: String, stayLabel: String,
    background: Color, primary: Color, secondary: Color, text: Color, error: Color,
    headingStyle: TextStyle, bodyStyle: TextStyle, shape: Shape, buttonShape: Shape,
    onDismiss: () -> Unit, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val stay = remember { FocusRequester() }
    val exit = remember { FocusRequester() }
    LaunchedEffect(Unit) { stay.requestFocus() }
    Surface(modifier.widthIn(max = 480.dp).semantics { paneTitle = title }
        .clickable(remember { MutableInteractionSource() }, indication = null) {},
        color = background, shape = shape) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(title, style = headingStyle, fontWeight = FontWeight.Black, color = primary)
            Text(message, style = bodyStyle, color = secondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, modifier = Modifier.focusRequester(stay).focusProperties {
                    next = exit; previous = exit; left = exit; right = exit; up = exit; down = exit
                }) { Text(stayLabel, color = text, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onExit, modifier = Modifier.focusRequester(exit).focusProperties {
                    next = stay; previous = stay; left = stay; right = stay; up = stay; down = stay
                }, colors = ButtonDefaults.buttonColors(containerColor = error, contentColor = Color.White),
                    shape = buttonShape) { Text(exitLabel, fontWeight = FontWeight.Black) }
            }
        }
    }
}
