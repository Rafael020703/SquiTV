package rsv.squitv.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rsv.squitv.R
import rsv.squitv.data.model.BackupOption
import rsv.squitv.ui.viewmodel.settings.LocalTransferViewModel

@Composable
fun LocalTransferDialog(
    receive: Boolean,
    selected: Set<BackupOption>,
    onDismiss: () -> Unit,
    viewModel: LocalTransferViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activity = LocalContext.current.findTransferActivity()
    var entered by rememberSaveable { mutableStateOf(false) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var address by rememberSaveable { mutableStateOf("") }
    var showAddress by rememberSaveable { mutableStateOf(false) }
    val request = state.pending
    val acceptFocus = remember { FocusRequester() }
    val optionLabels = request?.options?.sortedBy { it.ordinal }?.map { stringResource(backupOptionLabel(it)) }.orEmpty()

    LaunchedEffect(receive) {
        val current = viewModel.state.value
        if (!entered || (!current.receiving && !current.starting && !current.busy && !current.discovering &&
                !current.completed && current.notice == null)) {
            entered = true
            if (receive) viewModel.startReceiver(selected) else viewModel.startDiscovery(selected)
        }
    }
    LaunchedEffect(request?.id) { if (request != null) acceptFocus.requestFocus() }
    DisposableEffect(lifecycle, viewModel, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && activity?.isChangingConfigurations != true) viewModel.stop()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (activity?.isChangingConfigurations != true) viewModel.stop()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (request != null) R.string.local_sync_approval_title
            else if (receive) R.string.local_sync_receive else R.string.local_sync_send)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (request != null) {
                    Text(stringResource(R.string.local_sync_request_from, request.name), style = MaterialTheme.typography.titleMedium)
                    Text(request.address, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.local_sync_approval_description))
                    Text(optionLabels.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.local_sync_approval_timeout), style = MaterialTheme.typography.bodySmall)
                } else if (!state.completed) {
                    Text(stringResource(if (receive) R.string.local_sync_receive_instructions else R.string.local_sync_send_instructions))
                    Text(stringResource(R.string.local_sync_selected_count, selected.size), style = MaterialTheme.typography.bodySmall)
                    if (state.starting || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.status?.let { Text(stringResource(it), style = MaterialTheme.typography.titleSmall) }
                    if (receive) {
                        if (state.receiving && !state.busy) {
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(stringResource(R.string.local_sync_ready), style = MaterialTheme.typography.titleMedium)
                                    Text(stringResource(R.string.local_sync_expiry), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            TextButton(onClick = { showAddress = !showAddress }) {
                                Text(stringResource(R.string.local_sync_connection_details))
                            }
                            if (showAddress) state.addresses.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        } else if (!state.starting && !state.busy) {
                            Button(onClick = { viewModel.startReceiver(selected) }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.local_sync_start_receive))
                            }
                        }
                    } else if (!state.busy) {
                        if (!manual) {
                            if (state.peers.isEmpty()) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text(stringResource(R.string.local_sync_searching))
                            } else {
                                Text(stringResource(R.string.local_sync_choose_device), style = MaterialTheme.typography.titleSmall)
                                state.peers.forEach { device ->
                                    FilledTonalButton(onClick = { viewModel.send(device) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                        Column(Modifier.fillMaxWidth()) {
                                            Text(device.name, style = MaterialTheme.typography.titleMedium)
                                            Text(device.address.hostAddress.orEmpty(), style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                            TextButton(onClick = { viewModel.startDiscovery(selected) }) {
                                Text(stringResource(R.string.local_sync_refresh))
                            }
                        } else {
                            OutlinedTextField(value = address, onValueChange = { address = it.take(160) },
                                singleLine = true, modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.local_sync_address)) })
                            Button(enabled = address.isNotBlank(), onClick = { viewModel.sendManual(address) },
                                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.local_sync_send_now)) }
                        }
                        TextButton(onClick = { manual = !manual }) {
                            Text(stringResource(if (manual) R.string.local_sync_auto_discovery else R.string.local_sync_manual))
                        }
                        Text(stringResource(R.string.local_sync_network_hint), style = MaterialTheme.typography.bodySmall)
                    }
                }
                state.notice?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
                if (state.completed) {
                    OutlinedButton(onClick = {
                        if (receive) viewModel.startReceiver(selected) else viewModel.startDiscovery(selected)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (receive) R.string.local_sync_start_receive else R.string.local_sync_send_again))
                    }
                }
            }
        },
        confirmButton = {
            if (request != null) {
                Button(onClick = { viewModel.respond(request.id, true) }, modifier = Modifier.focusRequester(acceptFocus)) {
                    Text(stringResource(R.string.local_sync_accept))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(if (state.busy) R.string.cancel else R.string.close))
                }
            }
        },
        dismissButton = {
            if (request != null) {
                TextButton(onClick = { viewModel.respond(request.id, false) }) { Text(stringResource(R.string.local_sync_reject)) }
            }
        }
    )
}

private fun Context.findTransferActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val base = current.baseContext
        if (base === current) return null
        current = base
    }
    return current as? Activity
}
