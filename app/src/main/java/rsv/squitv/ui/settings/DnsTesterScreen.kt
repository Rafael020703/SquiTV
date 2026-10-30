package rsv.squitv.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import rsv.squitv.R
import rsv.squitv.core.ui.components.buttons.AppButton
import rsv.squitv.core.ui.components.buttons.AppSecondaryButton
import rsv.squitv.core.ui.components.navigation.AppHeader
import rsv.squitv.core.ui.theme.*
import rsv.squitv.ui.dashboard.PortalBackground
import rsv.squitv.ui.viewmodel.settings.DnsTesterUiState
import rsv.squitv.ui.viewmodel.settings.DnsTesterViewModel

@UnstableApi
@Composable
fun DnsTesterScreen(
    viewModel: DnsTesterViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    DnsTesterContent(
        uiState = uiState,
        normalizeUrl = viewModel::normalizeUrl,
        onBack = onBack,
        onRetest = viewModel::testAllDns,
        onOpenSwitchDialog = viewModel::openSwitchDialog,
        onDismissSwitchDialog = viewModel::dismissSwitchDialog,
        onSelectDnsToConfirm = viewModel::selectDnsToConfirm,
        onCancelConfirmSwitch = viewModel::cancelConfirmSwitch,
        onPerformSwitchDns = viewModel::performSwitchDns
    )
}

@Composable
fun DnsTesterContent(
    uiState: DnsTesterUiState,
    normalizeUrl: (String?) -> String,
    onBack: () -> Unit,
    onRetest: () -> Unit,
    onOpenSwitchDialog: () -> Unit,
    onDismissSwitchDialog: () -> Unit,
    onSelectDnsToConfirm: (String) -> Unit,
    onCancelConfirmSwitch: () -> Unit,
    onPerformSwitchDns: (String) -> Unit
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive

    val availableDns = uiState.availableDns
    val dnsStatusMap = uiState.dnsStatusMap
    val currentDns = uiState.currentDns

    val totalCount = availableDns.size
    val onlineCount = dnsStatusMap.values.count { it != -1L }
    val isCurrentDnsOnline = currentDns != null && (dnsStatusMap[currentDns] ?: -1L) != -1L
    val isCurrentDnsTested = currentDns != null && dnsStatusMap.containsKey(currentDns)

    PortalBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            AppHeader(
                title = stringResource(R.string.dns_tester_title),
                subtitle = stringResource(R.string.dns_tester_subtitle),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = responsive.dp(tokens.spacing.extraLarge), vertical = responsive.dp(tokens.spacing.large)),
                verticalArrangement = Arrangement.spacedBy(responsive.dp(tokens.spacing.large))
            ) {

                // --- ACTION BAR (Retestar) ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.dns_tester_title).uppercase(),
                        style = tokens.typography.label.copy(
                            fontSize = responsive.sp(14.sp),
                            fontWeight = FontWeight.Black,
                            letterSpacing = responsive.sp(2.sp)
                        ),
                        color = tokens.colors.primary
                    )

                    AppButton(
                        text = if (uiState.isTesting) stringResource(R.string.status_testing) else stringResource(R.string.retest_button),
                        onClick = onRetest,
                        enabled = !uiState.isTesting && !uiState.isSwitching && !uiState.isSyncing,
                        icon = Icons.Rounded.Refresh
                    )
                }

                // --- STATUS GENERAL CARD (Resumo Geral) ---
                DnsSummaryCard(
                    isTesting = uiState.isTesting,
                    isInternetConnected = uiState.isInternetConnected,
                    dnsStatusMap = dnsStatusMap,
                    onlineCount = onlineCount,
                    totalCount = totalCount
                )

                // --- FEEDBACK DE TROCA / SINCRONIZAÇÃO ---
                AnimatedVisibility(visible = uiState.switchStatusMessage != null || uiState.isSwitching || uiState.isSyncing) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = when (uiState.syncSuccess) {
                            true -> tokens.colors.success.copy(alpha = 0.15f)
                            false -> tokens.colors.error.copy(alpha = 0.15f)
                            else -> tokens.colors.primary.copy(alpha = 0.15f)
                        },
                        shape = tokens.shapes.medium,
                        border = BorderStroke(
                            responsive.dp(1.dp),
                            when (uiState.syncSuccess) {
                                true -> tokens.colors.success
                                false -> tokens.colors.error
                                else -> tokens.colors.primary
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(responsive.dp(16.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (uiState.isSwitching || uiState.isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(responsive.dp(20.dp)),
                                    color = tokens.colors.primary,
                                    strokeWidth = responsive.dp(2.dp)
                                )
                                Spacer(modifier = Modifier.width(responsive.dp(12.dp)))
                            } else {
                                Icon(
                                    imageVector = if (uiState.syncSuccess == true) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                                    contentDescription = null,
                                    tint = if (uiState.syncSuccess == true) tokens.colors.success else tokens.colors.error,
                                    modifier = Modifier.size(responsive.dp(22.dp))
                                )
                                Spacer(modifier = Modifier.width(responsive.dp(12.dp)))
                            }

                            Text(
                                text = uiState.switchStatusMessage ?: "",
                                style = tokens.typography.body.copy(fontSize = responsive.sp(13.sp)),
                                color = tokens.colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // --- DNS ATUAL (Active DNS Card) ---
                if (currentDns != null) {
                    ActiveDnsCard(
                        currentDns = currentDns,
                        ping = dnsStatusMap[currentDns],
                        isTested = isCurrentDnsTested,
                        isOnline = isCurrentDnsOnline,
                        isTesting = uiState.isTesting,
                        isInternetConnected = uiState.isInternetConnected,
                        hasAvailableAlternatives = onlineCount > 0,
                        onOpenSwitchDialog = onOpenSwitchDialog
                    )
                }

                // --- LISTA DE DNS (DNS Servers List) ---
                Text(
                    text = stringResource(R.string.configured_dns_servers_title),
                    style = tokens.typography.label.copy(
                        fontSize = responsive.sp(12.sp),
                        fontWeight = FontWeight.Black,
                        letterSpacing = responsive.sp(1.5.sp)
                    ),
                    color = tokens.colors.textSecondary,
                    modifier = Modifier.padding(top = responsive.dp(tokens.spacing.medium))
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(responsive.dp(tokens.spacing.medium))
                ) {
                    availableDns.forEachIndexed { index, dnsUrl ->
                        val ping = dnsStatusMap[dnsUrl]
                        val isCurrent = currentDns != null && normalizeUrl(dnsUrl) == normalizeUrl(currentDns)

                        DnsDetailRow(
                            index = index + 1,
                            dnsUrl = dnsUrl,
                            ping = ping,
                            isCurrent = isCurrent,
                            isTesting = uiState.isTesting && ping == null,
                            isInternetConnected = uiState.isInternetConnected,
                            onClick = {
                                if (!isCurrent && ping != null && ping != -1L) {
                                    onSelectDnsToConfirm(dnsUrl)
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(responsive.dp(tokens.spacing.giant)))
            }
        }
    }

    // --- DIÁLOGO DE SELEÇÃO DE DNS ---
    if (uiState.showSwitchDialog) {
        AlertDialog(
            onDismissRequest = onDismissSwitchDialog,
            containerColor = tokens.colors.backgroundSecondary,
            shape = tokens.shapes.extraLarge,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        tint = tokens.colors.primary,
                        modifier = Modifier.size(responsive.dp(28.dp))
                    )
                    Spacer(Modifier.width(responsive.dp(12.dp)))
                    Text(
                        text = stringResource(R.string.switch_dns_title).uppercase(),
                        style = tokens.typography.headline.copy(fontSize = responsive.sp(20.sp)),
                        fontWeight = FontWeight.Black,
                        color = tokens.colors.textPrimary
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = responsive.dp(8.dp)),
                    verticalArrangement = Arrangement.spacedBy(responsive.dp(8.dp))
                ) {
                    Text(
                        text = stringResource(R.string.switch_dns_instruction),
                        style = tokens.typography.body.copy(fontSize = responsive.sp(13.sp)),
                        color = tokens.colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(responsive.dp(4.dp)))

                    availableDns.forEach { dnsUrl ->
                        val ping = dnsStatusMap[dnsUrl]
                        val isOnline = ping != null && ping != -1L
                        val isCurrent = currentDns != null && normalizeUrl(dnsUrl) == normalizeUrl(currentDns)

                        Surface(
                            onClick = {
                                if (isOnline && !isCurrent) {
                                    onSelectDnsToConfirm(dnsUrl)
                                }
                            },
                            enabled = isOnline && !isCurrent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    responsive.dp(1.dp),
                                    when {
                                        isCurrent -> tokens.colors.primary
                                        isOnline -> tokens.colors.success.copy(alpha = 0.5f)
                                        else -> tokens.colors.error.copy(alpha = 0.3f)
                                    },
                                    tokens.shapes.medium
                                ),
                            shape = tokens.shapes.medium,
                            color = when {
                                isCurrent -> tokens.colors.primary.copy(alpha = 0.15f)
                                isOnline -> tokens.colors.surface
                                else -> Color.White.copy(alpha = 0.02f)
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(responsive.dp(12.dp)),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = dnsUrl.removePrefix("http://").removePrefix("https://"),
                                        style = tokens.typography.label.copy(fontSize = responsive.sp(13.sp)),
                                        fontWeight = FontWeight.Bold,
                                        color = if (isOnline) tokens.colors.textPrimary else tokens.colors.textDisabled
                                    )
                                    Text(
                                        text = dnsUrl,
                                        style = tokens.typography.caption.copy(fontSize = responsive.sp(11.sp)),
                                        color = tokens.colors.textSecondary
                                    )
                                }

                                Spacer(modifier = Modifier.width(responsive.dp(8.dp)))

                                when {
                                    isCurrent -> {
                                        Text(
                                            text = stringResource(R.string.status_in_use).uppercase(),
                                            style = tokens.typography.caption.copy(fontSize = responsive.sp(10.sp)),
                                            color = tokens.colors.primary,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                    isOnline -> {
                                        Text(
                                            text = "🟢 ${ping}ms",
                                            style = tokens.typography.caption.copy(fontSize = responsive.sp(11.sp)),
                                            color = tokens.colors.success,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = "🔴 ${stringResource(R.string.status_unavailable)}",
                                            style = tokens.typography.caption.copy(fontSize = responsive.sp(10.sp)),
                                            color = tokens.colors.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissSwitchDialog) {
                    Text(stringResource(R.string.cancel_button), color = tokens.colors.textSecondary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // --- DIÁLOGO DE CONFIRMAÇÃO DE TROCA ---
    if (uiState.confirmDnsToSwitch != null) {
        val targetDns = uiState.confirmDnsToSwitch
        val cleanTarget = targetDns.removePrefix("http://").removePrefix("https://")

        AlertDialog(
            onDismissRequest = onCancelConfirmSwitch,
            containerColor = tokens.colors.backgroundSecondary,
            shape = tokens.shapes.extraLarge,
            title = {
                Text(
                    text = stringResource(R.string.confirm_switch_dns_title),
                    style = tokens.typography.headline.copy(fontSize = responsive.sp(20.sp)),
                    fontWeight = FontWeight.Black,
                    color = tokens.colors.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(responsive.dp(8.dp))) {
                    Text(
                        text = stringResource(R.string.confirm_switch_instruction),
                        style = tokens.typography.body.copy(fontSize = responsive.sp(13.sp)),
                        color = tokens.colors.textSecondary
                    )

                    Surface(
                        color = tokens.colors.primary.copy(alpha = 0.1f),
                        shape = tokens.shapes.medium,
                        border = BorderStroke(responsive.dp(1.dp), tokens.colors.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = cleanTarget,
                            style = tokens.typography.title.copy(fontSize = responsive.sp(16.sp)),
                            fontWeight = FontWeight.Black,
                            color = tokens.colors.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(responsive.dp(12.dp))
                        )
                    }

                    Text(
                        text = stringResource(R.string.confirm_switch_notice),
                        style = tokens.typography.caption.copy(fontSize = responsive.sp(11.sp)),
                        color = tokens.colors.textSecondary
                    )
                }
            },
            confirmButton = {
                AppButton(
                    text = stringResource(R.string.use_dns_button),
                    onClick = { onPerformSwitchDns(targetDns) }
                )
            },
            dismissButton = {
                AppSecondaryButton(
                    text = stringResource(R.string.cancel_button),
                    onClick = onCancelConfirmSwitch
                )
            }
        )
    }
}

@Composable
fun DnsSummaryCard(
    isTesting: Boolean,
    isInternetConnected: Boolean,
    dnsStatusMap: Map<String, Long>,
    onlineCount: Int,
    totalCount: Int
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive

    val cardColor = when {
        !isInternetConnected -> tokens.colors.warning
        isTesting -> tokens.colors.primary
        onlineCount == totalCount && totalCount > 0 -> tokens.colors.success
        onlineCount > 0 -> tokens.colors.warning
        else -> tokens.colors.error
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(responsive.dp(1.5.dp), cardColor.copy(alpha = 0.4f), tokens.shapes.extraLarge),
        shape = tokens.shapes.extraLarge,
        color = Color.Black.copy(alpha = 0.6f),
        shadowElevation = responsive.dp(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(responsive.dp(tokens.spacing.large)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(responsive.dp(56.dp))
                    .background(cardColor.copy(alpha = 0.15f), CircleShape)
                    .border(responsive.dp(1.5.dp), cardColor.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(responsive.dp(24.dp)),
                        color = cardColor,
                        strokeWidth = responsive.dp(2.5.dp)
                    )
                } else {
                    Icon(
                        imageVector = when {
                            !isInternetConnected -> Icons.Rounded.WifiOff
                            onlineCount == totalCount && totalCount > 0 -> Icons.Rounded.CheckCircle
                            onlineCount > 0 -> Icons.Rounded.Warning
                            else -> Icons.Rounded.Error
                        },
                        contentDescription = null,
                        tint = cardColor,
                        modifier = Modifier.size(responsive.dp(32.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.width(responsive.dp(tokens.spacing.large)))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        !isInternetConnected -> stringResource(R.string.dns_status_no_internet_title)
                        isTesting -> stringResource(R.string.dns_status_testing_title)
                        onlineCount == totalCount && totalCount > 0 -> stringResource(R.string.dns_status_all_ok_title)
                        onlineCount > 0 -> stringResource(R.string.dns_status_attention_title)
                        else -> stringResource(R.string.dns_status_all_down_title)
                    },
                    style = tokens.typography.headline.copy(fontSize = responsive.sp(20.sp)),
                    fontWeight = FontWeight.Black,
                    color = tokens.colors.textPrimary
                )

                Text(
                    text = when {
                        !isInternetConnected -> stringResource(R.string.dns_status_no_internet_desc)
                        isTesting -> stringResource(R.string.dns_status_testing_desc)
                        onlineCount == totalCount && totalCount > 0 -> stringResource(R.string.dns_status_all_ok_desc)
                        onlineCount > 0 -> stringResource(R.string.dns_status_attention_desc)
                        else -> stringResource(R.string.dns_status_all_down_desc)
                    },
                    style = tokens.typography.body.copy(fontSize = responsive.sp(13.sp)),
                    color = tokens.colors.textSecondary
                )

                if (isInternetConnected) {
                    Spacer(modifier = Modifier.height(responsive.dp(4.dp)))

                    Text(
                        text = stringResource(R.string.dns_summary_count, onlineCount, totalCount).uppercase(),
                        style = tokens.typography.caption.copy(fontSize = responsive.sp(11.sp)),
                        fontWeight = FontWeight.Black,
                        color = cardColor
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveDnsCard(
    currentDns: String,
    ping: Long?,
    isTested: Boolean,
    isOnline: Boolean,
    isTesting: Boolean,
    isInternetConnected: Boolean,
    hasAvailableAlternatives: Boolean,
    onOpenSwitchDialog: () -> Unit
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive

    val cleanCurrent = currentDns.removePrefix("http://").removePrefix("https://")

    val borderColor = when {
        !isInternetConnected -> tokens.colors.warning
        !isTested || isTesting -> tokens.colors.border.copy(alpha = 0.3f)
        isOnline -> tokens.colors.success
        else -> tokens.colors.error
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(responsive.dp(1.5.dp), borderColor.copy(alpha = 0.5f), tokens.shapes.extraLarge),
        shape = tokens.shapes.extraLarge,
        color = Color.Black.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier.padding(responsive.dp(tokens.spacing.large))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Dns,
                        contentDescription = null,
                        tint = tokens.colors.primary,
                        modifier = Modifier.size(responsive.dp(20.dp))
                    )
                    Spacer(modifier = Modifier.width(responsive.dp(8.dp)))
                    Text(
                        text = stringResource(R.string.current_dns_label),
                        style = tokens.typography.label.copy(
                            fontSize = responsive.sp(12.sp),
                            fontWeight = FontWeight.Black,
                            letterSpacing = responsive.sp(1.5.sp)
                        ),
                        color = tokens.colors.primary
                    )
                }

                Surface(
                    color = when {
                        !isInternetConnected -> tokens.colors.warning.copy(alpha = 0.2f)
                        !isTested || isTesting -> tokens.colors.surface
                        isOnline -> tokens.colors.success.copy(alpha = 0.2f)
                        else -> tokens.colors.error.copy(alpha = 0.2f)
                    },
                    shape = tokens.shapes.small,
                    border = BorderStroke(
                        responsive.dp(1.dp),
                        when {
                            !isInternetConnected -> tokens.colors.warning
                            !isTested || isTesting -> tokens.colors.border
                            isOnline -> tokens.colors.success
                            else -> tokens.colors.error
                        }
                    )
                ) {
                    Text(
                        text = when {
                            !isInternetConnected -> stringResource(R.string.no_internet_error).uppercase()
                            isTesting -> stringResource(R.string.status_testing).uppercase()
                            isOnline -> "🟢 ${stringResource(R.string.status_available)} (${ping}ms)".uppercase()
                            else -> "🔴 ${stringResource(R.string.status_unavailable)}".uppercase()
                        },
                        style = tokens.typography.caption.copy(fontSize = responsive.sp(10.sp)),
                        fontWeight = FontWeight.Black,
                        color = when {
                            !isInternetConnected -> tokens.colors.warning
                            !isTested || isTesting -> tokens.colors.textSecondary
                            isOnline -> tokens.colors.success
                            else -> tokens.colors.error
                        },
                        modifier = Modifier.padding(horizontal = responsive.dp(8.dp), vertical = responsive.dp(4.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(responsive.dp(8.dp)))

            Text(
                text = cleanCurrent,
                style = tokens.typography.title.copy(fontSize = responsive.sp(20.sp)),
                fontWeight = FontWeight.Black,
                color = tokens.colors.textPrimary
            )

            Text(
                text = currentDns,
                style = tokens.typography.caption.copy(fontSize = responsive.sp(12.sp)),
                color = tokens.colors.textSecondary
            )

            Spacer(modifier = Modifier.height(responsive.dp(12.dp)))

            Text(
                text = when {
                    !isInternetConnected -> stringResource(R.string.dns_status_no_internet_desc)
                    isOnline -> stringResource(R.string.current_dns_available)
                    !isTested || isTesting -> stringResource(R.string.awaiting_test_result)
                    else -> stringResource(R.string.current_dns_unavailable)
                },
                style = tokens.typography.body.copy(fontSize = responsive.sp(13.sp)),
                color = if (!isOnline && isTested && !isTesting && isInternetConnected) tokens.colors.error else tokens.colors.textSecondary,
                fontWeight = FontWeight.Medium
            )

            if (!isOnline && isTested && !isTesting && isInternetConnected) {
                Spacer(modifier = Modifier.height(responsive.dp(6.dp)))
                Text(
                    text = stringResource(R.string.current_dns_recommendation),
                    style = tokens.typography.caption.copy(fontSize = responsive.sp(11.sp)),
                    color = tokens.colors.textSecondary
                )

                Spacer(modifier = Modifier.height(responsive.dp(16.dp)))

                AppButton(
                    text = stringResource(R.string.switch_dns_button),
                    onClick = onOpenSwitchDialog,
                    enabled = hasAvailableAlternatives,
                    icon = Icons.Rounded.SwapHoriz,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun DnsDetailRow(
    index: Int,
    dnsUrl: String,
    ping: Long?,
    isCurrent: Boolean,
    isTesting: Boolean,
    isInternetConnected: Boolean,
    onClick: () -> Unit
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive

    val cleanName = dnsUrl.removePrefix("http://").removePrefix("https://")
    val isOnline = ping != null && ping != -1L
    val isOffline = ping == -1L

    val borderColor = when {
        isCurrent -> tokens.colors.primary
        isOnline -> tokens.colors.success.copy(alpha = 0.4f)
        isOffline -> tokens.colors.error.copy(alpha = 0.4f)
        else -> tokens.colors.border.copy(alpha = 0.2f)
    }

    Surface(
        onClick = onClick,
        enabled = isOnline && !isCurrent && isInternetConnected,
        modifier = Modifier
            .fillMaxWidth()
            .border(responsive.dp(1.dp), borderColor, tokens.shapes.medium),
        shape = tokens.shapes.medium,
        color = when {
            isCurrent -> tokens.colors.primary.copy(alpha = 0.1f)
            isOnline -> tokens.colors.surface
            isOffline -> tokens.colors.error.copy(alpha = 0.05f)
            else -> tokens.colors.surface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(responsive.dp(tokens.spacing.medium)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = if (isCurrent) tokens.colors.primary else tokens.colors.surfaceElevated,
                    shape = CircleShape,
                    modifier = Modifier.size(responsive.dp(32.dp))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = index.toString(),
                            style = tokens.typography.label.copy(fontSize = responsive.sp(12.sp)),
                            fontWeight = FontWeight.Black,
                            color = if (isCurrent) Color.Black else tokens.colors.textPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(responsive.dp(12.dp)))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "DNS $index — $cleanName",
                            style = tokens.typography.label.copy(fontSize = responsive.sp(14.sp)),
                            fontWeight = FontWeight.Bold,
                            color = if (isOffline && isInternetConnected) tokens.colors.textDisabled else tokens.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (isCurrent) {
                            Spacer(modifier = Modifier.width(responsive.dp(8.dp)))
                            Surface(
                                color = tokens.colors.primary.copy(alpha = 0.2f),
                                shape = tokens.shapes.small,
                                border = BorderStroke(responsive.dp(1.dp), tokens.colors.primary)
                            ) {
                                Text(
                                    text = stringResource(R.string.status_in_use).uppercase(),
                                    style = tokens.typography.caption.copy(fontSize = responsive.sp(9.sp)),
                                    fontWeight = FontWeight.Black,
                                    color = tokens.colors.primary,
                                    modifier = Modifier.padding(horizontal = responsive.dp(6.dp), vertical = responsive.dp(2.dp))
                                )
                            }
                        }
                    }

                    Text(
                        text = dnsUrl,
                        style = tokens.typography.caption.copy(fontSize = responsive.sp(11.sp)),
                        color = tokens.colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(responsive.dp(12.dp)))

            when {
                !isInternetConnected -> {
                    Text(
                        text = stringResource(R.string.status_test_error),
                        style = tokens.typography.caption.copy(fontSize = responsive.sp(11.sp)),
                        fontWeight = FontWeight.Bold,
                        color = tokens.colors.textDisabled
                    )
                }
                isTesting || ping == null -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(responsive.dp(16.dp)),
                        color = tokens.colors.primary,
                        strokeWidth = responsive.dp(2.dp)
                    )
                }
                isOnline -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(responsive.dp(8.dp))
                                .background(tokens.colors.success, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(responsive.dp(6.dp)))
                        Text(
                            text = "${stringResource(R.string.status_available)} (${ping}ms)",
                            style = tokens.typography.caption.copy(fontSize = responsive.sp(12.sp)),
                            fontWeight = FontWeight.Bold,
                            color = tokens.colors.success
                        )
                    }
                }
                isOffline -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(responsive.dp(8.dp))
                                .background(tokens.colors.error, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(responsive.dp(6.dp)))
                        Text(
                            text = stringResource(R.string.status_unavailable),
                            style = tokens.typography.caption.copy(fontSize = responsive.sp(12.sp)),
                            fontWeight = FontWeight.Bold,
                            color = tokens.colors.error
                        )
                    }
                }
            }
        }
    }
}
