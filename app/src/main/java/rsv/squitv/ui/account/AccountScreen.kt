package rsv.squitv.ui.account

import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import rsv.squitv.util.AppVersionProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import rsv.squitv.R
import rsv.squitv.core.ui.components.buttons.*
import rsv.squitv.core.ui.components.navigation.AppHeader
import rsv.squitv.core.ui.theme.*
import rsv.squitv.data.model.UserInfo
import rsv.squitv.ui.dashboard.PortalBackground
import rsv.squitv.ui.viewmodel.MainViewModel
import rsv.squitv.util.NetworkUtils
import rsv.squitv.util.rememberWindowInfo
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun AccountScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val accountInfo by viewModel.accountInfo.collectAsStateWithLifecycle()
    
    LaunchedEffect(Unit) {
        viewModel.refreshAccountInfo()
        viewModel.refreshStats()
    }
    
    val context = LocalContext.current
    val deviceId = remember { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) }
    val ipAddress = remember { NetworkUtils.getIPAddress(true) }
    val credentials = viewModel.credentials

    PortalBackground {
        AccountContent(
            accountInfo = accountInfo,
            deviceId = deviceId,
            ipAddress = ipAddress,
            serverUrl = credentials?.baseUrl ?: "N/A",
            username = credentials?.username ?: accountInfo?.username ?: "N/A",
            onBack = onBack,
            onRefresh = { viewModel.refreshAccountInfo() },
            onLogout = { viewModel.logout(); onBack() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun AccountContent(
    accountInfo: UserInfo?,
    deviceId: String,
    ipAddress: String,
    serverUrl: String,
    username: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit
) {
    val tokens = AppDesignSystem
    
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = System.currentTimeMillis()
        }
    }

    val expiryTimestamp = accountInfo?.expDate?.toLongOrNull() ?: 0L
    val isExpiringSoon = expiryTimestamp > 0 && (expiryTimestamp * 1000 - currentTime < 24 * 60 * 60 * 1000)
    val isExpiringCritical = expiryTimestamp > 0 && (expiryTimestamp * 1000 - currentTime < 60 * 60 * 1000)

    val timeLeft = remember(expiryTimestamp, currentTime) {
        if (expiryTimestamp <= 0L || expiryTimestamp > 4000000000L) "ILIMITADO"
        else {
            val diff = expiryTimestamp * 1000 - currentTime
            if (diff <= 0) "EXPIRADO"
            else if (diff < 24 * 60 * 60 * 1000) {
                val h = diff / (1000 * 60 * 60)
                val m = (diff / (1000 * 60)) % 60
                val s = (diff / 1000) % 60
                String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
            } else {
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(expiryTimestamp * 1000))
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "critical")
    val alpha by if (isExpiringCritical) {
        infiniteTransition.animateFloat(
            initialValue = 0.4f, targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(500), repeatMode = RepeatMode.Reverse),
            label = "alpha"
        )
    } else {
        remember { mutableStateOf(1f) }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = "CONTA",
            subtitle = "Informações da sessão ativa e servidor",
            onBack = onBack,
            actions = {
                AppIconButton(icon = Icons.Rounded.Refresh, onClick = onRefresh)
            }
        )

        if (accountInfo == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = tokens.colors.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = tokens.spacing.large, vertical = tokens.spacing.small),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Identification (Compact, No big icon)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = tokens.spacing.small)
                ) {
                    Text(
                        text = username.uppercase(),
                        style = tokens.typography.headline,
                        fontWeight = FontWeight.Black,
                        color = tokens.colors.textPrimary,
                        letterSpacing = 1.sp
                    )

                    val status = accountInfo.status?.lowercase() ?: ""
                    val statusColor = if (status == "active") tokens.colors.success else tokens.colors.error

                    Surface(
                        modifier = Modifier.padding(top = 4.dp),
                        color = statusColor.copy(alpha = 0.1f),
                        shape = tokens.shapes.extraLarge,
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = (accountInfo.status ?: "CONECTADO").uppercase(),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = tokens.typography.caption,
                            fontWeight = FontWeight.Black,
                            color = statusColor,
                            letterSpacing = 1.5.sp
                        )
                    }
                }

                // Compact Info Cards Container (No Scroll, perfectly fitting viewport)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 750.dp)
                        .weight(1f, fill = false)
                        .padding(vertical = tokens.spacing.small),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.small)
                ) {
                    SectionHeader("DADOS DE ACESSO")
                    
                    Surface(
                        color = tokens.colors.surface.copy(alpha = 0.1f),
                        shape = tokens.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(tokens.spacing.medium),
                            verticalArrangement = Arrangement.spacedBy(tokens.spacing.small)
                        ) {
                            SystemInfoRow("LOGIN", username, Icons.Rounded.Person)
                            SystemInfoRow("SERVIDOR", serverUrl, Icons.Rounded.CloudQueue)
                            SystemInfoRow("CONEXÕES", "${accountInfo.activeCons ?: "0"} / ${accountInfo.maxConnections ?: "0"}", Icons.Rounded.Dns)
                            SystemInfoRow(
                                label = "VALIDADE", 
                                value = timeLeft, 
                                icon = if (isExpiringSoon) Icons.Rounded.Timer else Icons.Rounded.CalendarToday,
                                highlight = isExpiringCritical,
                                highlightAlpha = alpha
                            )
                        }
                    }

                    SectionHeader("DETALHES DO SISTEMA")

                    Surface(
                        color = tokens.colors.surface.copy(alpha = 0.08f),
                        shape = tokens.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(tokens.spacing.medium),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val context = LocalContext.current
                            val appVersion = remember(context) { AppVersionProvider.getFormattedVersionName(context).uppercase() }
                            MiniSystemInfo("IP", ipAddress, Icons.Rounded.Public)
                            MiniSystemInfo("DISPOSITIVO", deviceId.take(8).uppercase(), Icons.Rounded.Fingerprint)
                            MiniSystemInfo("VERSÃO", appVersion, Icons.Rounded.Info)
                        }
                    }
                }

                // Bottom Logout Button (Always fully visible without scrolling)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 750.dp)
                        .padding(bottom = tokens.spacing.small)
                ) {
                    AppButton(
                        text = "SAIR DA CONTA",
                        onClick = onLogout,
                        useGradient = false,
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val tokens = AppDesignSystem
    Text(
        text = title, 
        style = tokens.typography.label, 
        fontWeight = FontWeight.Black, 
        color = tokens.colors.primary, 
        letterSpacing = 2.sp
    )
}

@Composable
fun SystemInfoRow(
    label: String, 
    value: String, 
    icon: ImageVector, 
    highlight: Boolean = false,
    highlightAlpha: Float = 1f
) {
    val tokens = AppDesignSystem
    val tintColor = if (highlight) tokens.colors.error.copy(alpha = highlightAlpha) else tokens.colors.primary
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tintColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(tokens.spacing.medium))
            Text(text = label, style = tokens.typography.caption, color = tokens.colors.textSecondary, fontWeight = FontWeight.Bold)
        }
        Text(
            text = value.uppercase(), 
            style = tokens.typography.body, 
            color = if (highlight) tokens.colors.error.copy(alpha = highlightAlpha) else tokens.colors.textPrimary, 
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun MiniSystemInfo(label: String, value: String, icon: ImageVector) {
    val tokens = AppDesignSystem
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tokens.colors.primary.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(tokens.spacing.small))
        Column {
            Text(text = label, style = tokens.typography.caption.copy(fontSize = 10.sp), color = tokens.colors.textSecondary)
            Text(text = value, style = tokens.typography.caption, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold)
        }
    }
}
