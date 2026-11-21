package rsv.squitv.ui.content.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rsv.squitv.R
import rsv.squitv.core.domain.interactor.CatalogPreparationState
import rsv.squitv.core.domain.interactor.PauseReason
import rsv.squitv.core.ui.components.buttons.AppButton
import rsv.squitv.core.ui.components.common.adaptiveFocus
import rsv.squitv.core.ui.theme.AppDesignSystem
import rsv.squitv.domain.model.CategoryMode
import rsv.squitv.shared.catalog.PreparationProgress

@Composable
fun CatalogPreparationIndicator(
    state: CatalogPreparationState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = AppDesignSystem
    if (state.totalItems <= 0) return
    if (PreparationProgress.isComplete(state.totalItems, state.processedItems, state.failedItems, state.error != null)) return

    val text = when {
        state.error != null -> stringResource(R.string.catalog_prep_interrupted)
        state.serverManaged -> stringResource(R.string.catalog_prep_server_progress, PreparationProgress.percent(state.totalItems, state.processedItems))
        state.isRunning -> stringResource(R.string.catalog_prep_progress, PreparationProgress.percent(state.totalItems, state.processedItems))
        state.isPaused -> stringResource(R.string.catalog_prep_paused)
        state.waitingForRetry -> stringResource(R.string.catalog_prep_retry_wait)
        else -> stringResource(R.string.catalog_prep_progress, PreparationProgress.percent(state.totalItems, state.processedItems))
    }

    Surface(
        onClick = onClick,
        shape = tokens.shapes.small,
        color = tokens.colors.surface.copy(alpha = 0.8f),
        border = BorderStroke(1.dp, tokens.colors.primary.copy(alpha = 0.3f)),
        modifier = modifier.padding(horizontal = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = if (state.isRunning) Icons.Rounded.Sync else if (state.isPaused) Icons.Rounded.PauseCircle else Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = if (state.isRunning) tokens.colors.primary else if (state.isPaused) tokens.colors.warning else tokens.colors.success,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = tokens.typography.caption.copy(fontSize = 11.sp),
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CatalogPreparationDialog(
    state: CatalogPreparationState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onSpeedChange: (rsv.squitv.domain.model.PreparationSpeed) -> Unit = {},
    onDismiss: () -> Unit
) {
    val tokens = AppDesignSystem

    val pauseText = when (state.pauseReason) {
        PauseReason.PLAYBACK_ACTIVE -> stringResource(R.string.catalog_prep_paused_playback)
        PauseReason.NETWORK_UNAVAILABLE -> stringResource(R.string.catalog_prep_paused_network)
        PauseReason.USER_PAUSED -> stringResource(R.string.catalog_prep_paused_user)
        PauseReason.APP_BACKGROUND, PauseReason.SYNCING_OR_DOWNLOADING, null -> stringResource(R.string.catalog_prep_paused)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.catalog_preparing_title).uppercase(),
                    style = tokens.typography.title,
                    fontWeight = FontWeight.Black,
                    color = tokens.colors.textPrimary
                )
                Text(
                    text = if (state.serverManaged) stringResource(R.string.catalog_prep_server_detail) else stringResource(R.string.catalog_preparing_detail),
                    style = tokens.typography.caption,
                    color = tokens.colors.primary
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())
            ) {
                LocalizedPreparationProgress(state, tokens.colors.primary, tokens.colors.textSecondary)
                state.error?.let { Text(it, color = tokens.colors.error, style = tokens.typography.caption) }
                if (state.isPaused) {
                    Surface(
                        color = tokens.colors.warning.copy(alpha = 0.15f),
                        shape = tokens.shapes.small,
                        border = BorderStroke(1.dp, tokens.colors.warning)
                    ) {
                        Text(
                            text = pauseText,
                            style = tokens.typography.body,
                            fontWeight = FontWeight.Bold,
                            color = tokens.colors.warning,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                if (!state.currentItemName.isNullOrBlank()) {
                    Text(
                        text = if (state.inFlight > 1) stringResource(R.string.catalog_prep_batch_items, state.inFlight, state.currentItemName.orEmpty())
                            else stringResource(R.string.catalog_prep_current_item, state.currentItemName.orEmpty()),
                        style = tokens.typography.caption,
                        fontWeight = FontWeight.Bold,
                        color = tokens.colors.primary
                    )
                } else if (state.isCooldown) {
                    Text(
                        text = if (state.isThrottled) stringResource(R.string.catalog_prep_throttled) else stringResource(R.string.catalog_prep_cooldown),
                        style = tokens.typography.caption,
                        color = tokens.colors.textSecondary
                    )
                } else if (state.waitingForRetry) {
                    Text(stringResource(R.string.catalog_prep_waiting_items), style = tokens.typography.caption, color = tokens.colors.textSecondary)
                }

                if (!state.serverManaged) Text(
                    text = stringResource(R.string.catalog_prep_priority, stringResource(if (state.priorityType == "SERIES") R.string.catalog_prep_priority_series else R.string.catalog_prep_priority_movies)),
                    style = tokens.typography.body,
                    fontWeight = FontWeight.Bold,
                    color = tokens.colors.textPrimary
                )

                Text(
                    text = stringResource(R.string.catalog_prep_movies_count, state.vodProcessed, state.vodTotal, state.vodWithGenre),
                    style = tokens.typography.body,
                    color = tokens.colors.textPrimary
                )

                Text(
                    text = stringResource(R.string.catalog_prep_series_count, state.seriesProcessed, state.seriesTotal, state.seriesWithGenre),
                    style = tokens.typography.body,
                    color = tokens.colors.textPrimary
                )
                if (!state.serverManaged) Text(stringResource(R.string.catalog_prep_without_genre, state.noDataItems), style = tokens.typography.caption, color = tokens.colors.textSecondary)

                if (state.failedItems > 0) {
                    Text(
                        text = stringResource(R.string.catalog_prep_failures, state.failedItems),
                        style = tokens.typography.body,
                        color = tokens.colors.error
                    )
                }

                Spacer(Modifier.height(8.dp))

                if (!state.serverManaged) Text(
                    text = stringResource(R.string.catalog_prep_speed),
                    style = tokens.typography.caption,
                    fontWeight = FontWeight.Black,
                    color = tokens.colors.primary
                )

                if (!state.serverManaged) Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppButton(
                        text = stringResource(R.string.catalog_prep_slow),
                        onClick = { onSpeedChange(rsv.squitv.domain.model.PreparationSpeed.SLOW) },
                        enabled = state.speed != rsv.squitv.domain.model.PreparationSpeed.SLOW
                    )
                    AppButton(
                        text = stringResource(R.string.catalog_prep_normal),
                        onClick = { onSpeedChange(rsv.squitv.domain.model.PreparationSpeed.NORMAL) },
                        enabled = state.speed != rsv.squitv.domain.model.PreparationSpeed.NORMAL
                    )
                    AppButton(
                        text = stringResource(R.string.catalog_prep_fast),
                        onClick = { onSpeedChange(rsv.squitv.domain.model.PreparationSpeed.FAST) },
                        enabled = state.speed != rsv.squitv.domain.model.PreparationSpeed.FAST
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!state.serverManaged && state.isRunning) {
                    AppButton(
                        text = stringResource(R.string.catalog_prep_pause_action),
                        onClick = onPause
                    )
                } else if (!state.serverManaged && (state.isPaused || state.error != null)) {
                    AppButton(
                        text = stringResource(R.string.catalog_prep_resume_action),
                        onClick = onResume
                    )
                }
                if (!state.serverManaged && state.failedItems > 0) {
                    AppButton(
                        text = stringResource(R.string.catalog_prep_retry_action),
                        onClick = onRetry
                    )
                }
                AppButton(
                    text = stringResource(R.string.close),
                    onClick = onDismiss
                )
            }
        },
        containerColor = tokens.colors.surface,
        shape = tokens.shapes.medium
    )
}

@Composable
fun CategoryChoiceDialog(
    title: String,
    currentMode: CategoryMode,
    onConfirm: (CategoryMode) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = AppDesignSystem
    var selectedMode by remember { mutableStateOf(currentMode) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title.uppercase(),
                style = tokens.typography.title,
                fontWeight = FontWeight.Black,
                color = tokens.colors.textPrimary
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.first_use_prompt_desc),
                    style = tokens.typography.body,
                    color = tokens.colors.textSecondary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveFocus(shape = tokens.shapes.small)
                        .clickable { selectedMode = CategoryMode.PROVIDER }
                        .padding(8.dp)
                ) {
                    RadioButton(
                        selected = selectedMode == CategoryMode.PROVIDER,
                        onClick = { selectedMode = CategoryMode.PROVIDER }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.category_mode_provider),
                        style = tokens.typography.body,
                        fontWeight = FontWeight.Bold,
                        color = tokens.colors.textPrimary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveFocus(shape = tokens.shapes.small)
                        .clickable { selectedMode = CategoryMode.GENRE }
                        .padding(8.dp)
                ) {
                    RadioButton(
                        selected = selectedMode == CategoryMode.GENRE,
                        onClick = { selectedMode = CategoryMode.GENRE }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.category_mode_genre),
                        style = tokens.typography.body,
                        fontWeight = FontWeight.Bold,
                        color = tokens.colors.textPrimary
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(
                    text = stringResource(R.string.confirm),
                    onClick = { onConfirm(selectedMode) },
                    modifier = Modifier.focusRequester(focusRequester)
                )
                AppButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss
                )
            }
        },
        containerColor = tokens.colors.surface,
        shape = tokens.shapes.medium
    )
}


@Composable
internal fun LocalizedPreparationProgress(state: CatalogPreparationState, primary: Color, secondary: Color) {
    rsv.squitv.core.ui.components.common.PreparationProgressSummary(
        state.totalItems, state.processedItems, state.estimatedRemainingMs, state.isPaused,
        state.failedItems, primary, secondary,
        summaryText = stringResource(R.string.catalog_prep_summary,
            PreparationProgress.percent(state.totalItems, state.processedItems), state.processedItems, state.totalItems),
        estimateText = when {
            PreparationProgress.isComplete(state.totalItems, state.processedItems, state.failedItems, state.error != null) ->
                stringResource(R.string.catalog_prep_complete)
            state.error != null -> stringResource(R.string.catalog_prep_interrupted)
            state.isPaused -> stringResource(R.string.catalog_prep_estimate_paused)
            state.failedItems > 0 -> stringResource(R.string.catalog_prep_estimate_failures)
            state.estimatedRemainingMs != null -> stringResource(R.string.catalog_prep_estimate,
                if (state.estimatedRemainingMs < 60_000) stringResource(R.string.catalog_prep_under_minute)
                else PreparationProgress.duration(state.estimatedRemainingMs))
            else -> stringResource(R.string.catalog_prep_calculating)
        },
    )
}
