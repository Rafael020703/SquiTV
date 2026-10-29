package rsv.squitv.ui.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import rsv.squitv.data.model.BackupOption
import rsv.squitv.data.model.SettingsBackupCodec
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import rsv.squitv.R
import rsv.squitv.ui.viewmodel.settings.SettingsBackupViewModel

@Composable
fun SettingsBackupDialog(onDismiss: () -> Unit, viewModel: SettingsBackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var choosingFile by rememberSaveable { mutableStateOf(false) }
    var transferMode by rememberSaveable { mutableStateOf<String?>(null) }
    var showOptions by rememberSaveable(state.preview != null) { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        choosingFile = false
        if (uri != null) viewModel.exportTo(uri)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        choosingFile = false
        if (uri != null) viewModel.readFrom(uri)
    }
    val enabled = !state.busy && !choosingFile
    val available = remember(state.preview) { state.preview?.let(SettingsBackupCodec::available) ?: BackupOption.entries.toSet() }
    val selected = if (state.preview == null) state.exportSelected else state.importSelected

    transferMode?.let { mode ->
        LocalTransferDialog(receive = mode == "receive", selected = state.exportSelected,
            onDismiss = { transferMode = null })
        return
    }

    AlertDialog(
        onDismissRequest = { if (enabled) onDismiss() },
        title = { Text(stringResource(if (state.preview == null) R.string.settings_backup_title else R.string.settings_backup_confirm_title)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_backup_description))
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(stringResource(R.string.settings_backup_options_summary, selected.size, available.size),
                            style = MaterialTheme.typography.titleSmall)
                        TextButton(enabled = enabled, onClick = { showOptions = !showOptions }) {
                            Text(stringResource(if (showOptions) R.string.settings_backup_hide_options else R.string.settings_backup_customize))
                        }
                    }
                }
                if (showOptions) {
                    Text(stringResource(if (state.preview == null) R.string.settings_backup_choose_export else R.string.settings_backup_choose_import))
                    BackupChoiceRow(stringResource(R.string.settings_backup_select_all), selected.containsAll(available), enabled, viewModel::selectAll)
                    BackupOption.entries.filter { it in available }.forEach { option ->
                        BackupChoiceRow(stringResource(backupOptionLabel(option)), option in selected, enabled) { viewModel.toggleOption(option) }
                    }
                    Text(stringResource(R.string.settings_backup_exclusions), style = MaterialTheme.typography.bodySmall)
                    if (BackupOption.FAVORITES in selected) {
                        Text(stringResource(R.string.settings_backup_favorites_merge), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.settings_backup_profiles_hint), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (state.preview == null) {
                    Text(stringResource(R.string.settings_backup_local_section), style = MaterialTheme.typography.titleSmall)
                    Button(enabled = enabled && selected.isNotEmpty(), onClick = { transferMode = "receive" },
                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.local_sync_receive)) }
                    FilledTonalButton(enabled = enabled && selected.isNotEmpty(), onClick = { transferMode = "send" },
                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.local_sync_send)) }
                }

                if (state.preview != null) {
                    Text(stringResource(R.string.settings_backup_preview, state.matchedAccounts, state.skippedAccounts))
                    Text(stringResource(R.string.settings_backup_confirm_description))
                    if (state.skippedAccounts > 0) {
                        Text(stringResource(R.string.settings_backup_account_warning), color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    HorizontalDivider()
                    Text(stringResource(R.string.settings_backup_file_section), style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(
                        enabled = enabled && selected.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            viewModel.clearNotice()
                            choosingFile = true
                            try {
                                val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
                                exporter.launch("SquiTV-config-$timestamp.json")
                            } catch (_: ActivityNotFoundException) {
                                choosingFile = false
                                viewModel.pickerUnavailable()
                            }
                        }
                    ) { Text(stringResource(R.string.settings_backup_export)) }
                    OutlinedButton(
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            viewModel.clearNotice()
                            choosingFile = true
                            try {
                                importer.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                            } catch (_: ActivityNotFoundException) {
                                choosingFile = false
                                viewModel.pickerUnavailable()
                            }
                        }
                    ) { Text(stringResource(R.string.settings_backup_import)) }
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.notice?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            if (state.preview != null) {
                TextButton(enabled = enabled && selected.isNotEmpty(), onClick = viewModel::importPreview) {
                    Text(stringResource(R.string.settings_backup_import))
                }
            } else {
                TextButton(enabled = enabled, onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        },
        dismissButton = {
            if (state.preview != null) {
                TextButton(enabled = enabled, onClick = viewModel::cancelPreview) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

@Composable
internal fun BackupChoiceRow(label: String, checked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() }),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = null)
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

internal fun backupOptionLabel(option: BackupOption): Int = when (option) {
    BackupOption.PLAYER_ENGINE -> R.string.backup_option_player_engine
    BackupOption.BUFFER -> R.string.backup_option_buffer
    BackupOption.THEME -> R.string.backup_option_theme
    BackupOption.DATA_SAVER -> R.string.backup_option_data_saver
    BackupOption.ZOOM -> R.string.backup_option_zoom
    BackupOption.LANGUAGE -> R.string.backup_option_language
    BackupOption.SYNC_INTERVAL -> R.string.backup_option_sync_interval
    BackupOption.AUTO_PLAY -> R.string.backup_option_auto_play
    BackupOption.RESIZE_MODE -> R.string.backup_option_resize_mode
    BackupOption.DIAGNOSTICS -> R.string.backup_option_diagnostics
    BackupOption.COMPACT_MODE -> R.string.backup_option_compact_mode
    BackupOption.HIDE_BLOCKED -> R.string.backup_option_hide_blocked
    BackupOption.WIFI_ONLY -> R.string.backup_option_wifi_only
    BackupOption.SMART_DOWNLOADS -> R.string.backup_option_smart_downloads
    BackupOption.AUDIO_LANGUAGE -> R.string.backup_option_audio_language
    BackupOption.SUBTITLE_LANGUAGE -> R.string.backup_option_subtitle_language
    BackupOption.NOTIFICATIONS -> R.string.backup_option_notifications
    BackupOption.BACKGROUND_AUDIO -> R.string.backup_option_background_audio
    BackupOption.PIP -> R.string.backup_option_pip
    BackupOption.CATEGORY_ORDER -> R.string.backup_option_category_order
    BackupOption.HIDDEN_CATEGORIES -> R.string.backup_option_hidden_categories
    BackupOption.HOME_SECTIONS -> R.string.backup_option_home_sections
    BackupOption.DISPLAY_NAMES -> R.string.backup_option_display_names
    BackupOption.PROFILES -> R.string.backup_option_profiles
    BackupOption.FAVORITES -> R.string.backup_option_favorites
}
