package rsv.squitv.ui.settings

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rsv.squitv.R
import rsv.squitv.core.ui.components.buttons.AppButton
import rsv.squitv.core.ui.components.buttons.AppSecondaryButton
import rsv.squitv.core.ui.components.navigation.AppHeader
import rsv.squitv.core.ui.components.states.LoadingState
import rsv.squitv.core.ui.theme.AppDesignSystem
import rsv.squitv.domain.model.AppUpdateInfo
import rsv.squitv.ui.dashboard.PortalBackground
import rsv.squitv.ui.viewmodel.settings.UpdateUiState
import rsv.squitv.ui.viewmodel.settings.UpdateViewModel

@Composable
fun UpdatesScreen(
    viewModel: UpdateViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tokens = AppDesignSystem
    val responsive = tokens.responsive

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (uiState is UpdateUiState.PermissionRequired) {
                    viewModel.checkPermissionAndInstall()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    PortalBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            AppHeader(
                title = stringResource(R.string.app_updates_title),
                subtitle = stringResource(R.string.app_updates_subtitle),
                onBack = onBack
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = responsive.dp(tokens.spacing.extraLarge),
                        vertical = responsive.dp(tokens.spacing.large)
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier.widthIn(max = responsive.dp(700.dp)).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(responsive.dp(tokens.spacing.large))
                ) {
                    when (val state = uiState) {
                        is UpdateUiState.Checking -> {
                            LoadingState(message = stringResource(R.string.checking_updates))
                        }

                        is UpdateUiState.UpToDate -> {
                            UpToDateCard(
                                currentVersion = state.currentVersion,
                                onCheckAgain = { viewModel.checkForUpdates(force = true) }
                            )
                        }

                        is UpdateUiState.UpdateAvailable -> {
                            UpdateAvailableCard(
                                currentVersion = state.currentVersion,
                                updateInfo = state.updateInfo,
                                isIgnored = state.isIgnored,
                                onStartDownload = viewModel::startDownload,
                                onViewRelease = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, state.updateInfo.releaseUrl.toUri())
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                },
                                onIgnoreVersion = viewModel::ignoreVersion,
                                onCheckAgain = { viewModel.checkForUpdates(force = true) }
                            )
                        }

                        is UpdateUiState.Downloading -> {
                            DownloadingCard(
                                updateInfo = state.updateInfo,
                                progressPercent = state.progressPercent,
                                bytesDownloaded = state.bytesDownloaded,
                                totalBytes = state.totalBytes,
                                onCancel = viewModel::cancelDownload
                            )
                        }

                        is UpdateUiState.ReadyToInstall -> {
                            ReadyToInstallCard(
                                updateInfo = state.updateInfo,
                                onInstall = viewModel::installUpdate
                            )
                        }

                        is UpdateUiState.PermissionRequired -> {
                            PermissionRequiredCard(
                                updateInfo = state.updateInfo,
                                onOpenSettings = viewModel::openSettingsForPermission,
                                onRetryInstall = viewModel::checkPermissionAndInstall
                            )
                        }

                        is UpdateUiState.Error -> {
                            ErrorCard(
                                message = state.message,
                                onRetry = { viewModel.checkForUpdates(force = true) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpToDateCard(
    currentVersion: String,
    onCheckAgain: () -> Unit
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(responsive.dp(1.5.dp), tokens.colors.success.copy(alpha = 0.4f), tokens.shapes.extraLarge),
        shape = tokens.shapes.extraLarge,
        color = Color.Black.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier.padding(responsive.dp(tokens.spacing.extraLarge)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(responsive.dp(72.dp))
                    .background(tokens.colors.success.copy(alpha = 0.15f), CircleShape)
                    .border(responsive.dp(2.dp), tokens.colors.success, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = tokens.colors.success,
                    modifier = Modifier.size(responsive.dp(44.dp))
                )
            }

            Spacer(modifier = Modifier.height(responsive.dp(tokens.spacing.large)))

            Text(
                text = stringResource(R.string.up_to_date_title).uppercase(),
                style = tokens.typography.headline.copy(fontSize = responsive.sp(24.sp)),
                fontWeight = FontWeight.Black,
                color = tokens.colors.textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(responsive.dp(tokens.spacing.small)))

            Text(
                text = stringResource(R.string.up_to_date_desc),
                style = tokens.typography.body.copy(fontSize = responsive.sp(14.sp)),
                color = tokens.colors.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(responsive.dp(tokens.spacing.large)))

            Surface(
                color = tokens.colors.surface,
                shape = tokens.shapes.medium,
                border = BorderStroke(responsive.dp(1.dp), tokens.colors.border)
            ) {
                Text(
                    text = "${stringResource(R.string.current_version_label)} $currentVersion".uppercase(),
                    style = tokens.typography.label.copy(fontSize = responsive.sp(12.sp)),
                    fontWeight = FontWeight.Black,
                    color = tokens.colors.primary,
                    modifier = Modifier.padding(horizontal = responsive.dp(16.dp), vertical = responsive.dp(8.dp))
                )
            }

            Spacer(modifier = Modifier.height(responsive.dp(tokens.spacing.extraLarge)))

            AppSecondaryButton(
                text = stringResource(R.string.check_again_button),
                icon = Icons.Rounded.Refresh,
                onClick = onCheckAgain,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun formatPublishedDate(publishedAt: String?): String {
    if (publishedAt.isNullOrBlank()) return ""
    return try {
        val cleanDate = publishedAt.take(10)
        val parts = cleanDate.split("-")
        if (parts.size == 3) {
            "${parts[2]}/${parts[1]}/${parts[0]}"
        } else {
            cleanDate
        }
    } catch (_: Exception) {
        publishedAt.take(10)
    }
}

@Composable
private fun UpdateAvailableCard(
    currentVersion: String,
    updateInfo: AppUpdateInfo,
    isIgnored: Boolean,
    onStartDownload: () -> Unit,
    onViewRelease: () -> Unit,
    onIgnoreVersion: () -> Unit,
    onCheckAgain: () -> Unit
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive
    val formattedDate = remember(updateInfo.publishedAt) { formatPublishedDate(updateInfo.publishedAt) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(responsive.dp(1.5.dp), tokens.colors.primary.copy(alpha = 0.5f), tokens.shapes.extraLarge),
        shape = tokens.shapes.extraLarge,
        color = Color.Black.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier.padding(responsive.dp(tokens.spacing.extraLarge)),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(responsive.dp(48.dp))
                            .background(tokens.colors.primary.copy(alpha = 0.15f), CircleShape)
                            .border(responsive.dp(1.5.dp), tokens.colors.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            tint = tokens.colors.primary,
                            modifier = Modifier.size(responsive.dp(26.dp))
                        )
                    }
                    Spacer(modifier = Modifier.width(responsive.dp(16.dp)))
                    Column {
                        Text(
                            text = stringResource(R.string.update_available_title).uppercase(),
                            style = tokens.typography.headline.copy(fontSize = responsive.sp(20.sp)),
                            fontWeight = FontWeight.Black,
                            color = tokens.colors.textPrimary
                        )
                        Text(
                            text = updateInfo.releaseName,
                            style = tokens.typography.body.copy(fontSize = responsive.sp(14.sp)),
                            color = tokens.colors.textSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    color = tokens.colors.primary.copy(alpha = 0.2f),
                    shape = tokens.shapes.small,
                    border = BorderStroke(responsive.dp(1.dp), tokens.colors.primary)
                ) {
                    Text(
                        text = "v${updateInfo.versionName}",
                        style = tokens.typography.label.copy(fontSize = responsive.sp(12.sp)),
                        fontWeight = FontWeight.Black,
                        color = tokens.colors.primary,
                        modifier = Modifier.padding(horizontal = responsive.dp(12.dp), vertical = responsive.dp(6.dp))
                    )
                }
            }

            if (formattedDate.isNotBlank()) {
                Spacer(modifier = Modifier.height(responsive.dp(tokens.spacing.small)))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = responsive.dp(64.dp))
                ) {
                    Text(
                        text = "${stringResource(R.string.published_at_label)} $formattedDate",
                        style = tokens.typography.caption.copy(fontSize = responsive.sp(12.sp)),
                        color = tokens.colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(responsive.dp(tokens.spacing.large)))

            // Version Comparison
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.colors.surface.copy(alpha = 0.3f), tokens.shapes.medium)
                    .padding(responsive.dp(16.dp)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.current_version_label).uppercase(),
                        style = tokens.typography.caption.copy(fontSize = responsive.sp(11.sp)),
                        color = tokens.colors.textSecondary
                    )
                    Text(
                        text = currentVersion,
                        style = tokens.typography.title.copy(fontSize = responsive.sp(16.sp)),
                        fontWeight = FontWeight.Bold,
                        color = tokens.colors.textPrimary
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = tokens.colors.primary,
                    modifier = Modifier.size(responsive.dp(20.dp))
                )

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.available_version_label).uppercase(),
                        style = tokens.typography.caption.copy(fontSize = responsive.sp(11.sp)),
                        color = tokens.colors.primary
                    )
                    Text(
                        text = updateInfo.versionName,
                        style = tokens.typography.title.copy(fontSize = responsive.sp(16.sp)),
                        fontWeight = FontWeight.Black,
                        color = tokens.colors.primary
                    )
                }
            }

            // Changelog Box
            Spacer(modifier = Modifier.height(responsive.dp(tokens.spacing.large)))

            Text(
                text = stringResource(R.string.changelog_title).uppercase(),
                style = tokens.typography.label.copy(fontSize = responsive.sp(13.sp)),
                fontWeight = FontWeight.Black,
                color = tokens.colors.primary,
                letterSpacing = responsive.sp(1.sp)
            )

            Spacer(modifier = Modifier.height(responsive.dp(tokens.spacing.small)))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = responsive.dp(140.dp), max = responsive.dp(300.dp)),
                color = tokens.colors.surface.copy(alpha = 0.25f),
                shape = tokens.shapes.medium,
                border = BorderStroke(responsive.dp(1.dp), Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(responsive.dp(16.dp))
                ) {
                    Text(
                        text = updateInfo.changelog.ifBlank { stringResource(R.string.no_changelog) },
                        style = tokens.typography.body.copy(fontSize = responsive.sp(15.sp), lineHeight = responsive.sp(22.sp)),
                        color = tokens.colors.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(responsive.dp(tokens.spacing.extraLarge)))

            // Action Buttons
            Column(verticalArrangement = Arrangement.spacedBy(responsive.dp(12.dp))) {
                AppButton(
                    text = stringResource(R.string.download_update_button),
                    icon = Icons.Rounded.Download,
                    onClick = onStartDownload,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(responsive.dp(12.dp))
                ) {
                    AppSecondaryButton(
                        text = stringResource(R.string.view_release_button),
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        onClick = onViewRelease,
                        modifier = Modifier.weight(1f)
                    )

                    if (!isIgnored) {
                        AppSecondaryButton(
                            text = stringResource(R.string.ignore_version_button),
                            icon = Icons.Rounded.Block,
                            onClick = onIgnoreVersion,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadingCard(
    updateInfo: AppUpdateInfo,
    progressPercent: Int,
    bytesDownloaded: Long,
    totalBytes: Long,
    onCancel: () -> Unit
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive

    val formattedDownloaded = "%.1f MB".format(bytesDownloaded / (1024f * 1024f))
    val formattedTotal = if (totalBytes > 0) "%.1f MB".format(totalBytes / (1024f * 1024f)) else "N/A"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(responsive.dp(1.5.dp), tokens.colors.primary, tokens.shapes.extraLarge),
        shape = tokens.shapes.extraLarge,
        color = Color.Black.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier.padding(responsive.dp(tokens.spacing.extraLarge)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(responsive.dp(56.dp)),
                color = tokens.colors.primary,
                strokeWidth = responsive.dp(4.dp)
            )

            Spacer(modifier = Modifier.height(responsive.dp(tokens.spacing.large)))

            Text(
                text = stringResource(R.string.downloading_update).uppercase(),
                style = tokens.typography.headline.copy(fontSize = responsive.sp(20.sp)),
                fontWeight = FontWeight.Black,
                color = tokens.colors.textPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = updateInfo.releaseName,
                style = tokens.typography.body.copy(fontSize = responsive.sp(14.sp)),
                color = tokens.colors.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(responsive.dp(tokens.spacing.extraLarge)))

            // Progress bar
            LinearProgressIndicator(
                progress = { progressPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(responsive.dp(8.dp)),
                color = tokens.colors.primary,
                trackColor = Color.White.copy(alpha = 0.1f)
            )

            Spacer(modifier = Modifier.height(responsive.dp(12.dp)))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$progressPercent%",
                    style = tokens.typography.label.copy(fontSize = responsive.sp(14.sp)),
                    fontWeight = FontWeight.Black,
                    color = tokens.colors.primary
                )
                Text(
                    text = "$formattedDownloaded / $formattedTotal",
                    style = tokens.typography.caption.copy(fontSize = responsive.sp(12.sp)),
                    color = tokens.colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(responsive.dp(tokens.spacing.extraLarge)))

            AppSecondaryButton(
                text = stringResource(R.string.cancel_button),
                icon = Icons.Rounded.Close,
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ReadyToInstallCard(
    updateInfo: AppUpdateInfo,
    onInstall: () -> Unit
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(responsive.dp(1.5.dp), tokens.colors.success, tokens.shapes.extraLarge),
        shape = tokens.shapes.extraLarge,
        color = Color.Black.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier.padding(responsive.dp(tokens.spacing.extraLarge)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(responsive.dp(72.dp))
                    .background(tokens.colors.success.copy(alpha = 0.15f), CircleShape)
                    .border(responsive.dp(2.dp), tokens.colors.success, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = tokens.colors.success,
                    modifier = Modifier.size(responsive.dp(44.dp))
                )
            }

            Spacer(modifier = Modifier.height(responsive.dp(tokens.spacing.large)))

            Text(
                text = stringResource(R.string.download_complete_title).uppercase(),
                style = tokens.typography.headline.copy(fontSize = responsive.sp(22.sp)),
                fontWeight = FontWeight.Black,
                color = tokens.colors.textPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = updateInfo.releaseName,
                style = tokens.typography.body.copy(fontSize = responsive.sp(14.sp)),
                color = tokens.colors.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(responsive.dp(tokens.spacing.extraLarge)))

            AppButton(
                text = stringResource(R.string.install_now_button),
                icon = Icons.Rounded.SystemUpdate,
                onClick = onInstall,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PermissionRequiredCard(
    updateInfo: AppUpdateInfo,
    onOpenSettings: () -> Unit,
    onRetryInstall: () -> Unit
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(responsive.dp(1.5.dp), tokens.colors.warning, tokens.shapes.extraLarge),
        shape = tokens.shapes.extraLarge,
        color = Color.Black.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier.padding(responsive.dp(tokens.spacing.extraLarge)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(responsive.dp(72.dp))
                    .background(tokens.colors.warning.copy(alpha = 0.15f), CircleShape)
                    .border(responsive.dp(2.dp), tokens.colors.warning, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = tokens.colors.warning,
                    modifier = Modifier.size(responsive.dp(40.dp))
                )
            }

            Spacer(modifier = Modifier.height(responsive.dp(tokens.spacing.large)))

            Text(
                text = stringResource(R.string.permission_required_title).uppercase(),
                style = tokens.typography.headline.copy(fontSize = responsive.sp(22.sp)),
                fontWeight = FontWeight.Black,
                color = tokens.colors.textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(responsive.dp(8.dp)))

            Text(
                text = stringResource(R.string.permission_required_desc),
                style = tokens.typography.body.copy(fontSize = responsive.sp(14.sp)),
                color = tokens.colors.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(responsive.dp(tokens.spacing.extraLarge)))

            Column(verticalArrangement = Arrangement.spacedBy(responsive.dp(12.dp))) {
                AppButton(
                    text = stringResource(R.string.open_settings_button),
                    icon = Icons.Rounded.Settings,
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                )

                AppSecondaryButton(
                    text = stringResource(R.string.install_now_button),
                    icon = Icons.Rounded.SystemUpdate,
                    onClick = onRetryInstall,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(responsive.dp(1.5.dp), tokens.colors.error, tokens.shapes.extraLarge),
        shape = tokens.shapes.extraLarge,
        color = Color.Black.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier.padding(responsive.dp(tokens.spacing.extraLarge)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(responsive.dp(72.dp))
                    .background(tokens.colors.error.copy(alpha = 0.15f), CircleShape)
                    .border(responsive.dp(2.dp), tokens.colors.error, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Error,
                    contentDescription = null,
                    tint = tokens.colors.error,
                    modifier = Modifier.size(responsive.dp(44.dp))
                )
            }

            Spacer(modifier = Modifier.height(responsive.dp(tokens.spacing.large)))

            Text(
                text = stringResource(R.string.update_error_title).uppercase(),
                style = tokens.typography.headline.copy(fontSize = responsive.sp(20.sp)),
                fontWeight = FontWeight.Black,
                color = tokens.colors.textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(responsive.dp(8.dp)))

            Text(
                text = message,
                style = tokens.typography.body.copy(fontSize = responsive.sp(14.sp)),
                color = tokens.colors.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(responsive.dp(tokens.spacing.extraLarge)))

            AppButton(
                text = stringResource(R.string.try_again_button),
                icon = Icons.Rounded.Refresh,
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
