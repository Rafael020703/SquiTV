package rsv.squitv.ui.content.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rsv.squitv.R
import rsv.squitv.core.ui.theme.*
import rsv.squitv.core.ui.components.common.adaptiveFocus

@Composable
fun CategoryListItem(
    name: String, 
    modifier: Modifier = Modifier,
    count: Int? = null,
    isSelected: Boolean, 
    onClick: () -> Unit,
    onFocus: () -> Unit = {}
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive
    var isFocused by remember { mutableStateOf(false) }
    
    val textColor by animateColorAsState(
        targetValue = if (isFocused) Color.Black 
                    else if (isSelected) tokens.colors.primary 
                    else tokens.colors.textSecondary,
        label = "textColor"
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) tokens.colors.primary 
                    else if (isSelected) tokens.colors.primary.copy(alpha = 0.15f) 
                    else Color.Transparent,
        label = "bgColor"
    )

    val displayText = remember(name, count) {
        if (count != null) "$name ($count)" else name
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = responsive.dp(tokens.spacing.small), vertical = responsive.dp(tokens.spacing.tiny))
            .adaptiveFocus(
                shape = tokens.shapes.medium,
                onFocus = { focused ->
                    isFocused = focused
                    if (focused) {
                        onFocus()
                    }
                }
            )
            .clickable { onClick() }
            .background(backgroundColor, tokens.shapes.medium)
            .padding(vertical = responsive.dp(tokens.spacing.medium), horizontal = responsive.dp(tokens.spacing.large))
    ) {
        Text(
            text = displayText.uppercase(),
            color = textColor,
            style = tokens.typography.label.copy(fontSize = responsive.sp(tokens.typography.label.fontSize)),
            fontWeight = if (isFocused || isSelected) FontWeight.Black else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SortMenuDialog(
    currentSort: String, 
    onSortChanged: (String) -> Unit, 
    onDismiss: () -> Unit,
    showRecent: Boolean = true,
    showRating: Boolean = false
) {
    val tokens = AppDesignSystem
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = "ORDENAR POR", 
                style = tokens.typography.headline, 
                fontWeight = FontWeight.Black, 
                color = tokens.colors.primary 
            ) 
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.small)) {
                val options = mutableListOf<Triple<String, String, ImageVector>>()
                options.add(Triple("NAME", "Nome (A-Z)", Icons.Rounded.SortByAlpha))
                if (showRecent) options.add(Triple("RECENT", "Lançamento", Icons.Rounded.Schedule))
                if (showRating) options.add(Triple("RATING", "Melhores Notas", Icons.Rounded.Star))

                options.forEach { (id, label, icon) ->
                    Surface(
                        onClick = { onSortChanged(id); onDismiss() },
                        shape = tokens.shapes.medium,
                        color = if (currentSort == id) tokens.colors.primary.copy(alpha = 0.1f) else Color.Transparent,
                        border = if (currentSort == id) BorderStroke(1.dp, tokens.colors.primary.copy(alpha = 0.5f)) else null
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(tokens.spacing.medium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon, 
                                contentDescription = null, 
                                tint = if (currentSort == id) tokens.colors.primary else tokens.colors.textSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(tokens.spacing.large))
                            Text(
                                text = label.uppercase(), 
                                style = tokens.typography.body, 
                                color = if (currentSort == id) tokens.colors.primary else tokens.colors.textPrimary,
                                fontWeight = if (currentSort == id) FontWeight.Black else FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("FECHAR", color = tokens.colors.textSecondary, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = tokens.colors.backgroundSecondary,
        shape = tokens.shapes.extraLarge
    )
}

@Composable
fun PinUnlockDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val tokens = AppDesignSystem
    var pinValue by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = stringResource(R.string.locked_category_title).uppercase(),
                style = tokens.typography.headline,
                fontWeight = FontWeight.Black,
                color = tokens.colors.primary
            ) 
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.locked_category_msg),
                    style = tokens.typography.body,
                    color = tokens.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(tokens.spacing.large))
                OutlinedTextField(
                    value = pinValue,
                    onValueChange = { if (it.length <= 4) pinValue = it },
                    label = { Text(stringResource(R.string.pin_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = tokens.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = tokens.colors.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(pinValue) },
                colors = ButtonDefaults.buttonColors(containerColor = tokens.colors.primary, contentColor = Color.Black),
                shape = tokens.shapes.medium
            ) {
                Text(stringResource(R.string.unlock_button).uppercase(), fontWeight = FontWeight.Black)
            }
        },
        containerColor = tokens.colors.backgroundSecondary,
        shape = tokens.shapes.extraLarge
    )
}
