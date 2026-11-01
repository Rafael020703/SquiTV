package rsv.squitv.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rsv.squitv.R
import rsv.squitv.core.ui.components.buttons.AppButton
import rsv.squitv.core.ui.theme.AppDesignSystem
import rsv.squitv.data.pairing.PairingState
import rsv.squitv.ui.viewmodel.LocalLoginViewModel

@Composable
fun LocalLoginScreen(viewModel: LocalLoginViewModel, onBack: () -> Unit) {
    val status by viewModel.serverStatus.collectAsStateWithLifecycle()
    val port by viewModel.serverPort.collectAsStateWithLifecycle()
    val ip by viewModel.localIp.collectAsStateWithLifecycle()
    val session by viewModel.currentSession.collectAsStateWithLifecycle()
    val colors = AppDesignSystem.colors
    val ready = port > 0 && ip != "127.0.0.1"
    val retry = status in setOf(PairingState.EXPIRED, PairingState.CANCELLED) ||
        (status == PairingState.ERROR && port == 0) || (port > 0 && !ready)
    val message = when {
        port > 0 && !ready -> R.string.local_login_no_network
        status == PairingState.ERROR && port > 0 -> R.string.local_login_auth_error
        status == PairingState.ERROR -> R.string.local_login_server_error
        status == PairingState.EXPIRED -> R.string.local_login_expired
        status == PairingState.AUTHENTICATING -> R.string.local_login_authenticating
        status == PairingState.SUCCESS -> R.string.local_login_success
        status == PairingState.CANCELLED -> R.string.local_login_cancelled
        else -> R.string.local_login_waiting
    }
    Box(Modifier.fillMaxSize().background(colors.background).padding(16.dp),
        contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth()
                .background(colors.surface, AppDesignSystem.shapes.large)
                .verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.local_login_title), color = colors.textPrimary,
                fontSize = 25.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(stringResource(R.string.local_login_instructions), color = colors.textSecondary,
                textAlign = TextAlign.Center)
            if (ready) {
                Text("http://$ip:$port", modifier = Modifier.fillMaxWidth()
                    .background(colors.background, AppDesignSystem.shapes.medium).padding(16.dp),
                    color = colors.primary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center)
                Text(stringResource(R.string.local_login_code), color = colors.textSecondary, fontSize = 12.sp)
                Text(session?.pairingCode.orEmpty(), color = colors.textPrimary,
                    fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            }
            Text(stringResource(message), textAlign = TextAlign.Center,
                color = if (status == PairingState.ERROR || retry) colors.warning else colors.textSecondary)
            if (retry) AppButton(text = stringResource(R.string.local_login_retry),
                onClick = viewModel::startPairing, modifier = Modifier.fillMaxWidth())
            AppButton(text = stringResource(R.string.cancel), onClick = { viewModel.cancelPairing(); onBack() },
                modifier = Modifier.fillMaxWidth())
        }
    }
}
