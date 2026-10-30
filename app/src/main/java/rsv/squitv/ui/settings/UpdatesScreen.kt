package rsv.squitv.ui.settings

import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import rsv.squitv.R
import rsv.squitv.core.ui.components.navigation.AppHeader
import rsv.squitv.core.ui.theme.AppDesignSystem
import rsv.squitv.domain.model.AppUpdateInfo
import rsv.squitv.ui.dashboard.PortalBackground
import rsv.squitv.ui.updates.*
import rsv.squitv.ui.viewmodel.settings.UpdateUiState
import rsv.squitv.ui.viewmodel.settings.UpdateViewModel
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun UpdatesScreen(viewModel: UpdateViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val colors = AppDesignSystem.colors
    val actionFocus = remember { FocusRequester() }
    // Read the ViewModel at resume time; do not capture an obsolete UI state in the observer.
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state::class) {
        delay(150)
        runCatching { actionFocus.requestFocus() }
    }
    val info = when (val value = state) {
        is UpdateUiState.UpdateAvailable -> value.updateInfo
        is UpdateUiState.Downloading -> value.updateInfo
        is UpdateUiState.Verifying -> value.updateInfo
        is UpdateUiState.ReadyToInstall -> value.updateInfo
        is UpdateUiState.PermissionRequired -> value.updateInfo
        is UpdateUiState.OpeningInstaller -> value.updateInfo
        is UpdateUiState.InstallerOpened -> value.updateInfo
        is UpdateUiState.Error -> value.updateInfo
        else -> null
    }
    val title = stringResource(when (state) {
        UpdateUiState.Checking -> R.string.checking_updates
        is UpdateUiState.DebugBuild -> R.string.updater_debug_title
        is UpdateUiState.UpToDate -> R.string.up_to_date_title
        is UpdateUiState.UpdateAvailable -> R.string.update_available_title
        is UpdateUiState.Downloading -> R.string.downloading_update
        is UpdateUiState.Verifying -> R.string.updater_verifying
        is UpdateUiState.ReadyToInstall -> R.string.download_complete_title
        is UpdateUiState.PermissionRequired -> R.string.permission_required_title
        is UpdateUiState.OpeningInstaller -> R.string.updater_opening_installer
        is UpdateUiState.InstallerOpened -> R.string.updater_installer_opened
        is UpdateUiState.Error -> R.string.updater_error_title
    })
    val description = when (val value = state) {
        is UpdateUiState.Error -> value.message
        else -> stringResource(when (state) {
            UpdateUiState.Checking -> R.string.updater_checking_desc
            is UpdateUiState.DebugBuild -> R.string.updater_debug_desc
            is UpdateUiState.UpToDate -> R.string.up_to_date_desc
            is UpdateUiState.UpdateAvailable -> R.string.updater_available_desc
            is UpdateUiState.Downloading -> R.string.updater_downloading_desc
            is UpdateUiState.Verifying -> R.string.updater_verifying_desc
            is UpdateUiState.ReadyToInstall -> R.string.updater_install_desc
            is UpdateUiState.PermissionRequired -> R.string.updater_permission_desc
            is UpdateUiState.OpeningInstaller -> R.string.updater_install_desc
            else -> R.string.updater_installer_desc
        })
    }
    val icon = when (state) {
        is UpdateUiState.UpToDate -> Icons.Rounded.CheckCircle
        is UpdateUiState.DebugBuild -> Icons.Rounded.Code
        is UpdateUiState.Error -> Icons.Rounded.ErrorOutline
        is UpdateUiState.PermissionRequired -> Icons.Rounded.LockOpen
        is UpdateUiState.Downloading -> Icons.Rounded.Download
        is UpdateUiState.Verifying -> Icons.Rounded.VerifiedUser
        else -> Icons.Rounded.SystemUpdate
    }
    val primaryModifier = Modifier.fillMaxWidth().focusRequester(actionFocus)
    PortalBackground {
        Column(Modifier.fillMaxSize()) {
            AppHeader(title = stringResource(R.string.app_updates_title), subtitle = stringResource(R.string.updater_header_desc))
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    UpdateStatusCard(title, description, icon,
                        accent = when (state) {
                            is UpdateUiState.Error -> colors.error
                            is UpdateUiState.UpToDate -> colors.success
                            is UpdateUiState.PermissionRequired -> colors.warning
                            else -> colors.primary
                        }
                    ) {
                        UpdateVersionSummary(viewModel.currentVersionName, info)
                        if (info != null) {
                            ReleaseMetadata(info)
                            UpdateStages(when (state) {
                                is UpdateUiState.Verifying -> 1
                                is UpdateUiState.ReadyToInstall, is UpdateUiState.PermissionRequired,
                                is UpdateUiState.OpeningInstaller, is UpdateUiState.InstallerOpened -> 2
                                else -> 0
                            })
                        }
                        when (val value = state) {
                            UpdateUiState.Checking, is UpdateUiState.Verifying, is UpdateUiState.OpeningInstaller -> {
                                LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.primary)
                            }
                            is UpdateUiState.DebugBuild -> Unit
                            is UpdateUiState.UpToDate -> UpdateAction(stringResource(R.string.updater_check), { viewModel.checkForUpdates(true) }, primaryModifier, icon = Icons.Rounded.Refresh)
                            is UpdateUiState.UpdateAvailable -> {
                                if (value.isIgnored) Text(stringResource(R.string.updater_ignored_desc), color = colors.textSecondary, style = AppDesignSystem.typography.body)
                                UpdateAction(stringResource(R.string.updater_download), viewModel::startDownload, primaryModifier, icon = Icons.Rounded.Download)
                                if (!value.isIgnored) UpdateAction(stringResource(R.string.updater_silence), viewModel::ignoreVersion, Modifier.fillMaxWidth(), primary = false, icon = Icons.Rounded.NotificationsOff)
                            }
                            is UpdateUiState.Downloading -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (value.totalBytes > 0) {
                                        LinearProgressIndicator(progress = { value.progressPercent / 100f }, modifier = Modifier.fillMaxWidth(), color = colors.primary)
                                    } else LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.primary)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(if (value.totalBytes > 0) "${value.progressPercent}%" else stringResource(R.string.updater_download), color = colors.textPrimary)
                                        Text(if (value.totalBytes > 0) stringResource(R.string.updater_download_bytes,
                                            Formatter.formatShortFileSize(context, value.bytesDownloaded), Formatter.formatShortFileSize(context, value.totalBytes))
                                            else Formatter.formatShortFileSize(context, value.bytesDownloaded), color = colors.textSecondary)
                                    }
                                }
                                UpdateAction(stringResource(R.string.updater_cancel), viewModel::cancelDownload, primaryModifier, primary = false)
                            }
                            is UpdateUiState.ReadyToInstall -> UpdateAction(stringResource(R.string.updater_install), viewModel::installUpdate, primaryModifier, icon = Icons.Rounded.SystemUpdate)
                            is UpdateUiState.PermissionRequired -> UpdateAction(stringResource(R.string.updater_allow_install), viewModel::openSettingsForPermission, primaryModifier, icon = Icons.Rounded.Settings)
                            is UpdateUiState.InstallerOpened -> UpdateAction(stringResource(R.string.updater_reopen_installer), viewModel::installUpdate, primaryModifier, icon = Icons.Rounded.SystemUpdate)
                            is UpdateUiState.Error -> UpdateAction(stringResource(R.string.updater_retry), viewModel::retry, primaryModifier, icon = Icons.Rounded.Refresh)
                        }
                    }
                    if (info != null && state !is UpdateUiState.Downloading && state !is UpdateUiState.Verifying && state !is UpdateUiState.OpeningInstaller) {
                        UpdateReleaseNotes(info)
                        if (info.releaseUrl.startsWith("https://")) UpdateAction(
                            stringResource(R.string.updater_on_github),
                            onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, info.releaseUrl.toUri())) } },
                            modifier = Modifier.fillMaxWidth(), primary = false, icon = Icons.AutoMirrored.Rounded.OpenInNew
                        )
                    }
                    if (state is UpdateUiState.UpdateAvailable) UpdateAction(
                        stringResource(R.string.updater_check), { viewModel.checkForUpdates(true) }, Modifier.fillMaxWidth(), primary = false, icon = Icons.Rounded.Refresh
                    )
                }
            }
        }
    }
}

@Composable
private fun ReleaseMetadata(info: AppUpdateInfo) {
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val date = remember(info.publishedAt, locale) {
        runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }.parse(info.publishedAt.take(10))
                ?.let { DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(it) }
        }.getOrNull().orEmpty()
    }
    val metadata = listOfNotNull(
        info.apkSize.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) },
        date.takeIf { it.isNotBlank() }
    ).joinToString(" • ")
    if (metadata.isNotBlank()) Text(metadata, color = AppDesignSystem.colors.textSecondary, style = AppDesignSystem.typography.caption)
}

@Composable
private fun UpdateStages(active: Int) {
    val colors = AppDesignSystem.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(R.string.updater_step_download, R.string.updater_step_verify, R.string.updater_step_install).forEachIndexed { index, label ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val tint = if (index <= active) colors.primary else colors.textSecondary
                Box(Modifier.size(28.dp).background(tint.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    if (index < active) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = tint)
                    else Text("${index + 1}", color = tint, style = AppDesignSystem.typography.label)
                }
                Text(stringResource(label), color = tint, style = AppDesignSystem.typography.caption)
            }
        }
    }
}
