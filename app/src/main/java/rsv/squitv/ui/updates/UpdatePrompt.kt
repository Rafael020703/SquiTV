package rsv.squitv.ui.updates

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import rsv.squitv.R
import rsv.squitv.core.ui.theme.AppDesignSystem
import rsv.squitv.core.ui.components.common.adaptiveFocus
import rsv.squitv.domain.model.AppUpdateInfo

private const val NOTICE_SECONDS = 10

@Composable
fun UpdatePrompt(info: AppUpdateInfo, onUpdate: () -> Unit, onIgnore: () -> Unit, onDismiss: () -> Unit) {
    val colors = AppDesignSystem.colors
    val focus = remember { FocusRequester() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val maxHeight = with(LocalDensity.current) { windowHeight.toDp() * 0.85f }.coerceAtLeast(160.dp)
    val dismiss by rememberUpdatedState(onDismiss)
    var remaining by rememberSaveable(info.versionName) { mutableIntStateOf(NOTICE_SECONDS) }
    LaunchedEffect(info.versionName, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (remaining > 0) { delay(1_000); remaining-- }
            dismiss()
        }
    }
    LaunchedEffect(info.versionName) { delay(150); runCatching { focus.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(24.dp).widthIn(max = 520.dp).fillMaxWidth()
                .heightIn(max = maxHeight),
            shape = AppDesignSystem.shapes.extraLarge, color = colors.surface,
            border = BorderStroke(1.dp, colors.border)
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.SystemUpdate, null, tint = colors.primary, modifier = Modifier.size(32.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.update_available_title), color = colors.textPrimary,
                            style = AppDesignSystem.typography.title, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.updater_version_ready, info.versionName), color = colors.primary,
                            style = AppDesignSystem.typography.label)
                    }
                }
                Text(stringResource(R.string.updater_notice_desc), color = colors.textSecondary, style = AppDesignSystem.typography.body)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    UpdateAction(stringResource(R.string.updater_view), onUpdate, Modifier.weight(1f).focusRequester(focus))
                    UpdateAction(stringResource(R.string.updater_later), onDismiss, Modifier.weight(1f), primary = false)
                }
                LinearProgressIndicator(progress = { remaining / NOTICE_SECONDS.toFloat() }, modifier = Modifier.fillMaxWidth(), color = colors.primary)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.updater_notice_timeout, remaining), color = colors.textSecondary, style = AppDesignSystem.typography.caption)
                    TextButton(onClick = onIgnore, modifier = Modifier.heightIn(min = 48.dp).adaptiveFocus(AppDesignSystem.shapes.button)) {
                        Text(stringResource(R.string.updater_silence), color = colors.textSecondary, style = AppDesignSystem.typography.caption)
                    }
                }
            }
        }
    }
}
