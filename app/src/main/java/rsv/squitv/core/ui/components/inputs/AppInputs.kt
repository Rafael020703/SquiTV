package rsv.squitv.core.ui.components.inputs

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import rsv.squitv.core.ui.theme.*

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    requireClickToEdit: Boolean = false
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive
    
    val height = responsive.dp(tokens.dimensions.minTouchTarget + tokens.spacing.small).coerceAtLeast(tokens.dimensions.minTouchTarget)
    val fontSize = responsive.sp(tokens.typography.body.fontSize)
    val labelSize = responsive.sp(tokens.typography.label.fontSize)

    var isFocused by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val isReadOnly = requireClickToEdit && !isEditing

    val effectiveTrailingIcon: @Composable (() -> Unit)? = if (isPassword) {
        {
            IconButton(
                onClick = { passwordVisible = !passwordVisible },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                    contentDescription = if (passwordVisible) "Ocultar senha" else "Mostrar senha",
                    tint = if (isFocused) tokens.colors.primary else tokens.colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    } else {
        trailingIcon
    }

    val animatedBorderColor by animateColorAsState(
        targetValue = if (isFocused) tokens.colors.primary else tokens.colors.textPrimary.copy(alpha = 0.12f),
        label = "textFieldBorder"
    )
    val animatedContainerColor by animateColorAsState(
        targetValue = if (isFocused) tokens.colors.primary.copy(alpha = 0.12f) else tokens.colors.textPrimary.copy(alpha = 0.05f),
        label = "textFieldContainer"
    )

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = isReadOnly,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .onFocusChanged { 
                isFocused = it.isFocused
                if (!it.isFocused) {
                    isEditing = false
                }
            }
            .pointerInput(requireClickToEdit) {
                if (requireClickToEdit) {
                    detectTapGestures(
                        onTap = {
                            isEditing = true
                            keyboardController?.show()
                        }
                    )
                }
            }
            .onKeyEvent { keyEvent ->
                if (requireClickToEdit && isFocused && !isEditing) {
                    if (keyEvent.type == KeyEventType.KeyDown && 
                        (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)) {
                        isEditing = true
                        keyboardController?.show()
                        return@onKeyEvent true
                    }
                }
                false
            }
            .then(
                if (isFocused) {
                    Modifier.shadow(
                        elevation = 12.dp,
                        shape = tokens.shapes.large,
                        ambientColor = tokens.colors.primary.copy(alpha = 0.4f),
                        spotColor = tokens.colors.primary
                    )
                } else Modifier
            ),
        placeholder = { 
            Text(
                text = placeholder.uppercase(), 
                style = tokens.typography.label.copy(fontSize = labelSize),
                color = if (isFocused) tokens.colors.primary.copy(alpha = 0.8f) else tokens.colors.textSecondary,
                fontWeight = FontWeight.Bold
            ) 
        },
        leadingIcon = leadingIcon,
        trailingIcon = effectiveTrailingIcon,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        shape = tokens.shapes.large,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
            imeAction = imeAction
        ),
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = animatedBorderColor,
            unfocusedBorderColor = animatedBorderColor,
            focusedContainerColor = animatedContainerColor,
            unfocusedContainerColor = animatedContainerColor,
            cursorColor = tokens.colors.primary,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        ),
        textStyle = tokens.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = fontSize)
    )
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Buscar...",
    leadingIcon: @Composable (() -> Unit)? = { Icon(Icons.Rounded.Search, null, tint = PrimaryCyan) },
    trailingIcon: @Composable (() -> Unit)? = null,
    onSearch: () -> Unit = {}
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        imeAction = ImeAction.Search,
        keyboardActions = KeyboardActions(onSearch = { onSearch() })
    )
}
