package rsv.squitv.ui.account

import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import rsv.squitv.R
import rsv.squitv.core.ui.components.buttons.AppButton
import rsv.squitv.core.ui.components.buttons.AppIconButton
import rsv.squitv.core.ui.components.common.adaptiveFocus
import rsv.squitv.core.ui.components.navigation.AppHeader
import rsv.squitv.core.ui.theme.*
import rsv.squitv.data.model.UserInfo
import rsv.squitv.data.model.XtreamCredentials
import rsv.squitv.ui.dashboard.PortalBackground
import rsv.squitv.ui.viewmodel.MainViewModel
import rsv.squitv.util.AppVersionProvider
import rsv.squitv.util.NetworkUtils
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
    val sessionStatus by viewModel.sessionStatus.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val customDisplayName by viewModel.customDisplayName.collectAsStateWithLifecycle()
    val dbStats by viewModel.dbStats.collectAsStateWithLifecycle()
    
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
            sessionStatus = sessionStatus,
            deviceId = deviceId,
            ipAddress = ipAddress,
            serverUrl = credentials?.baseUrl ?: "N/A",
            username = credentials?.username ?: accountInfo?.username ?: "N/A",
            customDisplayName = customDisplayName,
            savedAccounts = appSettings.accounts,
            currentAccountIndex = appSettings.currentAccountIndex,
            dbStats = dbStats,
            onBack = onBack,
            onRefresh = { viewModel.refreshAccountInfo() },
            onSaveDisplayName = { name -> viewModel.saveCustomDisplayName(name) },
            onClearDisplayName = { viewModel.clearCustomDisplayName() },
            onSwitchAccount = { index -> viewModel.switchAccount(index) },
            onRemoveAccount = { index -> viewModel.removeAccount(index) },
            onLogout = { viewModel.logout(); onBack() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun AccountContent(
    accountInfo: UserInfo?,
    sessionStatus: rsv.squitv.domain.model.SessionStatus = rsv.squitv.domain.model.SessionStatus.VALID,
    deviceId: String,
    ipAddress: String,
    serverUrl: String,
    username: String,
    customDisplayName: String? = null,
    savedAccounts: List<XtreamCredentials> = emptyList(),
    currentAccountIndex: Int = 0,
    dbStats: Triple<Int, Int, Int> = Triple(0, 0, 0),
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSaveDisplayName: (String) -> Unit = {},
    onClearDisplayName: () -> Unit = {},
    onSwitchAccount: (Int) -> Unit = {},
    onRemoveAccount: (Int) -> Unit = {},
    onLogout: () -> Unit
) {
    val tokens = AppDesignSystem
    
    val formattedExpDate = remember(accountInfo?.expDate) {
        formatExpirationDate(accountInfo?.expDate)
    }

    val statusText = remember(accountInfo?.status, sessionStatus) {
        if (sessionStatus == rsv.squitv.domain.model.SessionStatus.OFFLINE) {
            "MODO OFFLINE"
        } else {
            val s = accountInfo?.status?.lowercase() ?: ""
            if (s == "active" || s == "valid") "ATIVO"
            else if (s == "expired") "EXPIRADO"
            else if (s.isNotBlank()) s.uppercase()
            else "CONECTADO"
        }
    }

    val isStatusActive = statusText == "ATIVO" || statusText == "CONECTADO"
    val isStatusOffline = statusText == "MODO OFFLINE"
    val statusColor = when {
        isStatusActive -> tokens.colors.success
        isStatusOffline -> Color(0xFFFFC107)
        else -> tokens.colors.error
    }

    val displayName = remember(customDisplayName, username) {
        customDisplayName?.takeIf { it.isNotBlank() } ?: username
    }

    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = "CONTA",
            subtitle = "Painel da Conta e Servidor",
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
                    .verticalScroll(scrollState)
                    .padding(horizontal = tokens.spacing.large, vertical = tokens.spacing.small),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.large)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 750.dp),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.large)
                ) {
                    // SEÇÃO A — PERFIL DO USUÁRIO
                    Surface(
                        color = tokens.colors.surface.copy(alpha = 0.15f),
                        shape = tokens.shapes.extraLarge,
                        border = BorderStroke(1.dp, tokens.colors.primary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(tokens.spacing.large),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(tokens.spacing.medium)
                        ) {
                            var isEditingName by remember { mutableStateOf(false) }
                            var nameInput by remember(customDisplayName) { mutableStateOf(customDisplayName ?: "") }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Avatar Circle
                                Surface(
                                    modifier = Modifier.size(64.dp),
                                    shape = CircleShape,
                                    color = tokens.colors.primary.copy(alpha = 0.15f),
                                    border = BorderStroke(2.dp, tokens.colors.primary.copy(alpha = 0.4f))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = displayName.firstOrNull()?.uppercase() ?: "U",
                                            style = tokens.typography.headline,
                                            fontWeight = FontWeight.Black,
                                            color = tokens.colors.primary
                                        )
                                    }
                                }

                                Spacer(Modifier.width(tokens.spacing.large))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = displayName.uppercase(),
                                        style = tokens.typography.headline.copy(fontSize = 20.sp),
                                        fontWeight = FontWeight.Black,
                                        color = tokens.colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "USUÁRIO IPTV: @$username",
                                        style = tokens.typography.caption.copy(fontSize = 11.sp),
                                        color = tokens.colors.textSecondary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Status Badge
                                Surface(
                                    color = statusColor.copy(alpha = 0.12f),
                                    shape = tokens.shapes.small,
                                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = statusText,
                                        style = tokens.typography.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Black),
                                        color = statusColor,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            // Action Row for Display Name Editing
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!customDisplayName.isNullOrBlank()) {
                                    TextButton(
                                        onClick = onClearDisplayName,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "RESTAURAR NOME PADRÃO",
                                            fontSize = 11.sp,
                                            color = tokens.colors.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }

                                AppButton(
                                    text = if (isEditingName) "CANCELAR" else "EDITAR NOME DE EXIBIÇÃO",
                                    icon = if (isEditingName) Icons.Rounded.Close else Icons.Rounded.Edit,
                                    onClick = { isEditingName = !isEditingName },
                                    useGradient = false,
                                    modifier = Modifier.height(38.dp)
                                )
                            }

                            // Inline Name Editor Field
                            if (isEditingName) {
                                Surface(
                                    color = tokens.colors.surfaceElevated.copy(alpha = 0.5f),
                                    shape = tokens.shapes.medium,
                                    border = BorderStroke(1.dp, tokens.colors.primary.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(tokens.spacing.medium),
                                        verticalArrangement = Arrangement.spacedBy(tokens.spacing.small)
                                    ) {
                                        Text(
                                            text = "INFORME O NOME DE EXIBIÇÃO DESEJADO:",
                                            style = tokens.typography.caption.copy(fontSize = 10.sp),
                                            color = tokens.colors.textSecondary,
                                            fontWeight = FontWeight.Bold
                                        )

                                        OutlinedTextField(
                                            value = nameInput,
                                            onValueChange = { nameInput = it },
                                            placeholder = { Text("Ex: Rafael Casa") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = tokens.colors.primary,
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            ),
                                            shape = tokens.shapes.medium
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Button(
                                                onClick = {
                                                    val trimmed = nameInput.trim()
                                                    if (trimmed.isNotBlank()) {
                                                        onSaveDisplayName(trimmed)
                                                    } else {
                                                        onClearDisplayName()
                                                    }
                                                    isEditingName = false
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = tokens.colors.primary, contentColor = Color.Black),
                                                shape = tokens.shapes.medium
                                            ) {
                                                Text("SALVAR NOME", fontWeight = FontWeight.Black)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SEÇÃO B — STATUS E DADOS DE ACESSO
                    SectionHeader("STATUS DA ASSINATURA E SERVIDOR")
                    Surface(
                        color = tokens.colors.surface.copy(alpha = 0.1f),
                        shape = tokens.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(tokens.spacing.medium),
                            verticalArrangement = Arrangement.spacedBy(tokens.spacing.small)
                        ) {
                            AccountInfoRow("VALIDADE", formattedExpDate, Icons.Rounded.CalendarToday)
                            AccountInfoRow("CONEXÕES", "${accountInfo.activeCons ?: "0"} de ${accountInfo.maxConnections ?: "0"} ativas", Icons.Rounded.Dns)
                            AccountInfoRow("SERVIDOR", serverUrl, Icons.Rounded.CloudQueue)
                        }
                    }

                    // SEÇÃO C — CONTAS SALVAS (MULTI-CONTA)
                    if (savedAccounts.isNotEmpty()) {
                        SectionHeader("CONTAS SALVAS NESTE DISPOSITIVO (${savedAccounts.size})")

                        Surface(
                            color = tokens.colors.surface.copy(alpha = 0.08f),
                            shape = tokens.shapes.large,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(tokens.spacing.small),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                savedAccounts.forEachIndexed { index, account ->
                                    var showDeleteConfirm by remember { mutableStateOf(false) }
                                    val isCurrent = index == currentAccountIndex || (account.username == username && account.baseUrl == serverUrl)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                Icons.Rounded.AccountCircle,
                                                null,
                                                tint = if (isCurrent) tokens.colors.primary else tokens.colors.textSecondary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = account.username.uppercase(),
                                                    style = tokens.typography.label.copy(fontSize = 12.sp),
                                                    fontWeight = FontWeight.Bold,
                                                    color = tokens.colors.textPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = account.baseUrl,
                                                    style = tokens.typography.caption.copy(fontSize = 10.sp),
                                                    color = tokens.colors.textSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isCurrent) {
                                                Surface(
                                                    color = tokens.colors.primary.copy(alpha = 0.2f),
                                                    shape = tokens.shapes.small
                                                ) {
                                                    Text(
                                                        text = "SESSÃO ATIVA",
                                                        style = tokens.typography.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.Black),
                                                        color = tokens.colors.primary,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            } else {
                                                TextButton(
                                                    onClick = { onSwitchAccount(index) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text("ALTERAR", fontSize = 11.sp, color = tokens.colors.primary, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            IconButton(
                                                onClick = { showDeleteConfirm = true },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Delete,
                                                    "Remover conta",
                                                    tint = tokens.colors.error.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (showDeleteConfirm) {
                                        AlertDialog(
                                            onDismissRequest = { showDeleteConfirm = false },
                                            containerColor = tokens.colors.backgroundSecondary,
                                            shape = tokens.shapes.extraLarge,
                                            title = {
                                                Text(
                                                    text = "REMOVER CONTA SALVA",
                                                    style = tokens.typography.headline.copy(fontSize = 16.sp),
                                                    fontWeight = FontWeight.Black,
                                                    color = tokens.colors.error
                                                )
                                            },
                                            text = {
                                                Text(
                                                    text = "Deseja realmente remover a conta ${account.username} (${account.baseUrl}) das contas salvas?",
                                                    style = tokens.typography.body.copy(fontSize = 12.sp),
                                                    color = tokens.colors.textSecondary
                                                )
                                            },
                                            confirmButton = {
                                                Button(
                                                    onClick = {
                                                        showDeleteConfirm = false
                                                        onRemoveAccount(index)
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = tokens.colors.error)
                                                ) {
                                                    Text("REMOVER", fontWeight = FontWeight.Black, color = Color.White)
                                                }
                                            },
                                            dismissButton = {
                                                TextButton(onClick = { showDeleteConfirm = false }) {
                                                    Text("CANCELAR", color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // SEÇÃO D — SISTEMA E CATÁLOGO
                    SectionHeader("DISPOSITIVO E CATÁLOGO LOCAL")
                    Surface(
                        color = tokens.colors.surface.copy(alpha = 0.08f),
                        shape = tokens.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(tokens.spacing.medium),
                            verticalArrangement = Arrangement.spacedBy(tokens.spacing.small)
                        ) {
                            val context = LocalContext.current
                            val appVersion = remember(context) { AppVersionProvider.getFormattedVersionName(context).uppercase() }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MiniSystemInfo("IP DE REDE", ipAddress, Icons.Rounded.Public)
                                MiniSystemInfo("DISPOSITIVO ID", deviceId.take(8).uppercase(), Icons.Rounded.Fingerprint)
                                MiniSystemInfo("VERSÃO APP", appVersion, Icons.Rounded.Info)
                            }

                            if (dbStats.first > 0 || dbStats.second > 0 || dbStats.third > 0) {
                                HorizontalDivider(
                                    color = Color.White.copy(alpha = 0.06f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                AccountInfoRow(
                                    label = "CATÁLOGO SINCRONIZADO",
                                    value = "${dbStats.first} Canais | ${dbStats.second} Filmes | ${dbStats.third} Séries",
                                    icon = Icons.Rounded.Storage
                                )
                            }
                        }
                    }

                    // SEÇÃO E — AÇÕES DA CONTA
                    Spacer(Modifier.height(tokens.spacing.small))
                    AppButton(
                        text = "SAIR DA CONTA",
                        onClick = onLogout,
                        useGradient = false,
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    )
                    Spacer(Modifier.height(tokens.spacing.large))
                }
            }
        }
    }
}

fun formatExpirationDate(expDateRaw: String?): String {
    if (expDateRaw.isNullOrBlank() || expDateRaw == "null" || expDateRaw == "0") {
        return "Não informado"
    }
    val clean = expDateRaw.trim()
    val timestamp = clean.toLongOrNull() ?: return "Não informado"
    if (timestamp <= 0L) return "Não informado"

    val millis = if (clean.length <= 10) timestamp * 1000L else timestamp
    return try {
        val date = Date(millis)
        val cal = Calendar.getInstance().apply { time = date }
        val year = cal.get(Calendar.YEAR)
        if (year in 2000..2100) {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date)
        } else {
            "Não informado"
        }
    } catch (_: Exception) {
        "Não informado"
    }
}

@Composable
private fun SectionHeader(title: String) {
    val tokens = AppDesignSystem
    Text(
        text = title, 
        style = tokens.typography.label.copy(fontSize = 11.sp), 
        fontWeight = FontWeight.Black, 
        color = tokens.colors.primary, 
        letterSpacing = 1.5.sp
    )
}

@Composable
fun AccountInfoRow(
    label: String, 
    value: String, 
    icon: ImageVector
) {
    val tokens = AppDesignSystem
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false).padding(end = tokens.spacing.medium)
        ) {
            Icon(icon, null, tint = tokens.colors.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(tokens.spacing.medium))
            Text(text = label, style = tokens.typography.caption, color = tokens.colors.textSecondary, fontWeight = FontWeight.Bold)
        }
        Text(
            text = value.uppercase(), 
            style = tokens.typography.body.copy(fontSize = 13.sp), 
            color = tokens.colors.textPrimary, 
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
