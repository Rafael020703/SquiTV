package rsv.squitv.ui.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rsv.squitv.R
import rsv.squitv.core.ui.components.buttons.AppButton
import rsv.squitv.core.ui.components.common.adaptiveFocus
import rsv.squitv.core.ui.components.inputs.AppTextField
import rsv.squitv.core.ui.theme.*
import rsv.squitv.data.model.XtreamCredentials
import rsv.squitv.ui.dashboard.PortalBackground
import rsv.squitv.util.AppVersionProvider
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onOpenLocalLogin: (() -> Unit)? = null,
    onOpenGallery: (() -> Unit)? = null,
    onOpenUpdates: (() -> Unit)? = null,
    onLoginSuccess: () -> Unit
) {
    val username by viewModel.username.collectAsState()
    val password by viewModel.password.collectAsState()
    val url by viewModel.url.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val promptSaveAccount by viewModel.promptSaveAccount.collectAsState()
    val savedAccounts by viewModel.savedAccounts.collectAsState()

    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val tokens = AppDesignSystem

    LaunchedEffect(Unit) {
        delay(150)
        try {
            usernameFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) {
        viewModel.loginSuccess.collect {
            onLoginSuccess()
        }
    }

    LoginContent(
        username = username,
        password = password,
        url = url,
        isLoading = isLoading,
        errorMessage = errorMessage,
        updateInfo = updateInfo,
        savedAccounts = savedAccounts,
        onUsernameChange = viewModel::onUsernameChanged,
        onPasswordChange = viewModel::onPasswordChanged,
        onLoginClick = viewModel::login,
        onRestoreClick = { viewModel.restoreBackup(isAuto = false) },
        onSelectSavedAccount = viewModel::selectSavedAccount,
        onRemoveSavedAccount = viewModel::removeSavedAccount,
        onOpenLocalLogin = onOpenLocalLogin,
        onOpenGallery = onOpenGallery,
        onOpenUpdates = onOpenUpdates,
        usernameFocusRequester = usernameFocusRequester,
        passwordFocusRequester = passwordFocusRequester
    )

    // DIÁLOGO "DESEJA SALVAR OS DADOS DESTE ACESSO?"
    if (promptSaveAccount != null) {
        val accountToSave = promptSaveAccount!!
        AlertDialog(
            onDismissRequest = { viewModel.confirmSaveAccount(accountToSave, save = false) },
            containerColor = tokens.colors.backgroundSecondary,
            shape = tokens.shapes.extraLarge,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Key,
                    contentDescription = null,
                    tint = tokens.colors.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "SALVAR CONTA",
                    style = tokens.typography.headline.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.Black,
                    color = tokens.colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Deseja salvar os dados deste acesso (${accountToSave.username}) para a próxima vez?",
                    style = tokens.typography.body.copy(fontSize = 13.sp),
                    color = tokens.colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmSaveAccount(accountToSave, save = true) },
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.colors.primary)
                ) {
                    Text("SALVAR", fontWeight = FontWeight.Black, color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.confirmSaveAccount(accountToSave, save = false) }
                ) {
                    Text("AGORA NÃO", color = tokens.colors.textSecondary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun LoginContent(
    username: String,
    password: String,
    url: String,
    isLoading: Boolean,
    errorMessage: String?,
    updateInfo: rsv.squitv.domain.model.AppUpdateInfo? = null,
    savedAccounts: List<XtreamCredentials> = emptyList(),
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onSelectSavedAccount: (XtreamCredentials) -> Unit = {},
    onRemoveSavedAccount: (Int) -> Unit = {},
    onOpenLocalLogin: (() -> Unit)? = null,
    onOpenGallery: (() -> Unit)? = null,
    onOpenUpdates: (() -> Unit)? = null,
    usernameFocusRequester: FocusRequester,
    passwordFocusRequester: FocusRequester
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive

    val isPortrait = responsive.heightDp > responsive.widthDp
    val isCompact = responsive.widthDp < 600.dp || isPortrait

    PortalBackground(
        showAtmosphere = true,
        atmosphereUrl = "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?auto=format&fit=crop&q=80&w=1920"
    ) {
        if (isCompact) {
            CompactLoginLayout(
                username = username,
                password = password,
                url = url,
                isLoading = isLoading,
                errorMessage = errorMessage,
                savedAccounts = savedAccounts,
                onUsernameChange = onUsernameChange,
                onPasswordChange = onPasswordChange,
                onLoginClick = onLoginClick,
                onRestoreClick = onRestoreClick,
                onSelectSavedAccount = onSelectSavedAccount,
                onRemoveSavedAccount = onRemoveSavedAccount,
                onOpenLocalLogin = onOpenLocalLogin,
                usernameFocusRequester = usernameFocusRequester,
                passwordFocusRequester = passwordFocusRequester
            )
        } else {
            ExpandedLoginLayout(
                username = username,
                password = password,
                url = url,
                isLoading = isLoading,
                errorMessage = errorMessage,
                savedAccounts = savedAccounts,
                onUsernameChange = onUsernameChange,
                onPasswordChange = onPasswordChange,
                onLoginClick = onLoginClick,
                onRestoreClick = onRestoreClick,
                onSelectSavedAccount = onSelectSavedAccount,
                onRemoveSavedAccount = onRemoveSavedAccount,
                onOpenLocalLogin = onOpenLocalLogin,
                usernameFocusRequester = usernameFocusRequester,
                passwordFocusRequester = passwordFocusRequester
            )
        }

        if (updateInfo != null && onOpenUpdates != null) {
            Surface(
                onClick = onOpenUpdates,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .adaptiveFocus(tokens.shapes.large),
                shape = tokens.shapes.large,
                color = Color(0xFF0D1322).copy(alpha = 0.85f),
                border = BorderStroke(1.5.dp, tokens.colors.primary)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(tokens.colors.primary, CircleShape)
                    )
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdate,
                        contentDescription = stringResource(R.string.update_available_login),
                        tint = tokens.colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "ATUALIZAÇÃO DISPONÍVEL (${updateInfo.versionName})",
                        style = tokens.typography.label.copy(fontSize = 12.sp, fontWeight = FontWeight.Black),
                        color = tokens.colors.textPrimary
                    )
                }
            }
        }

        if (onOpenGallery != null) {
            IconButton(
                onClick = onOpenGallery,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(tokens.colors.primary.copy(alpha = 0.2f), CircleShape)
                    .border(1.dp, tokens.colors.primary.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Science,
                    contentDescription = "Abrir Laboratório Visual",
                    tint = tokens.colors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun CompactLoginLayout(
    username: String,
    password: String,
    url: String,
    isLoading: Boolean,
    errorMessage: String?,
    savedAccounts: List<XtreamCredentials> = emptyList(),
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onSelectSavedAccount: (XtreamCredentials) -> Unit = {},
    onRemoveSavedAccount: (Int) -> Unit = {},
    onOpenLocalLogin: (() -> Unit)? = null,
    usernameFocusRequester: FocusRequester,
    passwordFocusRequester: FocusRequester
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive

    val screenWidth = responsive.widthDp
    val screenHeight = responsive.heightDp

    val horizontalPadding = if (screenWidth < 360.dp) 12.dp else if (screenWidth >= 600.dp) 32.dp else 20.dp
    val topSpacer = if (screenHeight < 600.dp) 8.dp else 16.dp
    val sectionSpacer = if (screenHeight < 600.dp) 14.dp else 24.dp
    val formMaxWidth = if (screenWidth >= 600.dp) 440.dp else 420.dp
    val surfacePadding = if (screenWidth < 360.dp) 16.dp else 22.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(topSpacer))

            // --- EMBLEMA CINEMÁTICO ---
            Box(
                modifier = Modifier
                    .size(if (screenHeight < 600.dp) 48.dp else 58.dp)
                    .shadow(
                        elevation = 14.dp,
                        shape = RoundedCornerShape(18.dp),
                        ambientColor = tokens.colors.primary.copy(alpha = 0.35f),
                        spotColor = tokens.colors.primary
                    )
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                tokens.colors.primary.copy(alpha = 0.25f),
                                tokens.colors.secondary.copy(alpha = 0.15f),
                                Color(0xFF0D1322).copy(alpha = 0.6f)
                            )
                        ),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .border(
                        BorderStroke(
                            1.5.dp,
                            Brush.linearGradient(
                                colors = listOf(
                                    tokens.colors.primary.copy(alpha = 0.7f),
                                    tokens.colors.primary.copy(alpha = 0.2f)
                                )
                            )
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LiveTv,
                    contentDescription = null,
                    modifier = Modifier.size(if (screenHeight < 600.dp) 28.dp else 34.dp),
                    tint = tokens.colors.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.institutional_title),
                style = tokens.typography.display.copy(
                    fontSize = if (screenWidth < 360.dp) 24.sp else 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                ),
                color = tokens.colors.textPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(R.string.institutional_desc),
                style = tokens.typography.body.copy(
                    fontSize = 13.sp,
                    lineHeight = 17.sp
                ),
                color = tokens.colors.textSecondary,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(sectionSpacer))

            // --- CARD DO FORMULÁRIO (VIDRO CINEMÁTICO) ---
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = formMaxWidth)
                    .shadow(
                        elevation = 24.dp,
                        shape = tokens.shapes.extraLarge,
                        ambientColor = tokens.colors.primary.copy(alpha = 0.25f),
                        spotColor = tokens.colors.primary.copy(alpha = 0.45f)
                    )
                    .border(
                        BorderStroke(
                            1.5.dp,
                            Brush.linearGradient(
                                colors = listOf(
                                    tokens.colors.primary.copy(alpha = 0.55f),
                                    tokens.colors.border.copy(alpha = 0.25f),
                                    tokens.colors.secondary.copy(alpha = 0.4f)
                                )
                            )
                        ),
                        tokens.shapes.extraLarge
                    ),
                shape = tokens.shapes.extraLarge,
                color = Color(0xFF0D1322).copy(alpha = 0.82f)
            ) {
                Column(
                    modifier = Modifier.padding(surfacePadding),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = stringResource(R.string.login_welcome_back),
                        style = tokens.typography.headline.copy(
                            fontSize = if (screenWidth < 360.dp) 20.sp else 22.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = tokens.colors.textPrimary
                    )

                    Text(
                        text = stringResource(R.string.login_subtitle_instruction),
                        style = tokens.typography.body.copy(fontSize = 13.sp),
                        color = tokens.colors.textSecondary,
                        fontWeight = FontWeight.Medium
                    )

                    // SAVED ACCOUNTS SELECTOR
                    if (savedAccounts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "CONTAS SALVAS",
                            style = tokens.typography.label.copy(fontSize = 11.sp),
                            color = tokens.colors.primary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(savedAccounts) { index, account ->
                                SavedAccountChip(
                                    account = account,
                                    isSelected = username == account.username && (url.isEmpty() || url == account.baseUrl),
                                    onSelect = { onSelectSavedAccount(account) },
                                    onDelete = { onRemoveSavedAccount(index) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // FIELDS WITH CYAN GLOW
                    AppTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        placeholder = stringResource(R.string.username_label).uppercase(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .focusRequester(usernameFocusRequester),
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Person,
                                null,
                                tint = tokens.colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        imeAction = ImeAction.Next,
                        keyboardActions = KeyboardActions(onNext = { passwordFocusRequester.requestFocus() }),
                        requireClickToEdit = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    AppTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        placeholder = stringResource(R.string.password_label).uppercase(),
                        isPassword = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .focusRequester(passwordFocusRequester),
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Lock,
                                null,
                                tint = tokens.colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        imeAction = ImeAction.Go,
                        keyboardActions = KeyboardActions(onGo = { if (!isLoading) onLoginClick() }),
                        requireClickToEdit = true
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = tokens.colors.error.copy(alpha = 0.15f),
                            shape = tokens.shapes.medium,
                            border = BorderStroke(1.dp, tokens.colors.error.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(
                                    elevation = 8.dp,
                                    shape = tokens.shapes.medium,
                                    ambientColor = tokens.colors.error.copy(alpha = 0.3f),
                                    spotColor = tokens.colors.error
                                )
                        ) {
                            Text(
                                text = errorMessage.uppercase(),
                                color = Color(0xFFFF6B6B),
                                style = tokens.typography.caption.copy(fontSize = 11.sp),
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // BOTÃO ENTRAR CINEMÁTICO COM GRADIENTE E ELEVAÇÃO
                    AppButton(
                        text = if (isLoading) stringResource(R.string.msg_connecting).uppercase() else stringResource(R.string.login_button).uppercase(),
                        onClick = onLoginClick,
                        enabled = !isLoading,
                        useGradient = true,
                        icon = Icons.AutoMirrored.Rounded.ArrowForward,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    )

                    if (onOpenLocalLogin != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        AppButton(
                            text = "LOGIN VIA REDE LOCAL",
                            onClick = onOpenLocalLogin,
                            useGradient = false,
                            icon = Icons.Rounded.Wifi,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // FOOTER
            val context = LocalContext.current
            val appVersion = remember(context) { AppVersionProvider.getFormattedVersionName(context).uppercase() }
            Text(
                text = "SQUI TV • $appVersion PREMIUM",
                style = tokens.typography.caption.copy(fontSize = 11.sp),
                color = tokens.colors.textSecondary.copy(alpha = 0.3f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ExpandedLoginLayout(
    username: String,
    password: String,
    url: String,
    isLoading: Boolean,
    errorMessage: String?,
    savedAccounts: List<XtreamCredentials> = emptyList(),
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onSelectSavedAccount: (XtreamCredentials) -> Unit = {},
    onRemoveSavedAccount: (Int) -> Unit = {},
    onOpenLocalLogin: (() -> Unit)? = null,
    usernameFocusRequester: FocusRequester,
    passwordFocusRequester: FocusRequester
) {
    val tokens = AppDesignSystem
    val responsive = tokens.responsive

    val screenWidth = responsive.widthDp
    val screenHeight = responsive.heightDp

    val isVeryCompactHeight = screenHeight < 420.dp
    val isCompactHeight = screenHeight < 520.dp

    val outerVerticalPadding = if (isVeryCompactHeight) 8.dp else if (isCompactHeight) 14.dp else 24.dp
    val outerHorizontalPadding = if (screenWidth < 900.dp) 16.dp else 32.dp
    val gapWidth = if (screenWidth < 900.dp) 24.dp else 44.dp

    val formPadding = if (isVeryCompactHeight) 12.dp else if (isCompactHeight) 18.dp else 26.dp
    val formSpacer = if (isVeryCompactHeight) 6.dp else if (isCompactHeight) 10.dp else 16.dp
    val formMaxWidth = if (screenWidth < 900.dp) 400.dp else if (screenWidth < 1400.dp) 460.dp else 480.dp
    val inputMinHeight = if (isVeryCompactHeight) 48.dp else 52.dp
    val buttonHeight = if (isVeryCompactHeight) 48.dp else 52.dp

    val logoSize = if (isVeryCompactHeight) 36.dp else if (isCompactHeight) 48.dp else 68.dp
    val logoIconSize = if (isVeryCompactHeight) 20.dp else if (isCompactHeight) 26.dp else 38.dp
    val displayTitleSize = if (isVeryCompactHeight) 22.sp else if (isCompactHeight) 28.sp else 38.sp
    val displaySubtitleSize = if (isVeryCompactHeight) 11.sp else if (isCompactHeight) 13.sp else 15.sp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 1020.dp)
                .padding(horizontal = outerHorizontalPadding, vertical = outerVerticalPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- LADO ESQUERDO: INSTITUCIONAL ADAPTATIVO ---
            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(logoSize)
                        .shadow(
                            elevation = if (isCompactHeight) 8.dp else 16.dp,
                            shape = RoundedCornerShape(if (isCompactHeight) 14.dp else 22.dp),
                            ambientColor = tokens.colors.primary.copy(alpha = 0.35f),
                            spotColor = tokens.colors.primary
                        )
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    tokens.colors.primary.copy(alpha = 0.25f),
                                    tokens.colors.secondary.copy(alpha = 0.15f),
                                    Color(0xFF0D1322).copy(alpha = 0.6f)
                                )
                            ),
                            shape = RoundedCornerShape(if (isCompactHeight) 14.dp else 22.dp)
                        )
                        .border(
                            BorderStroke(
                                1.5.dp,
                                Brush.linearGradient(
                                    colors = listOf(
                                        tokens.colors.primary.copy(alpha = 0.7f),
                                        tokens.colors.primary.copy(alpha = 0.2f)
                                    )
                                )
                            ),
                            shape = RoundedCornerShape(if (isCompactHeight) 14.dp else 22.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LiveTv,
                        contentDescription = null,
                        modifier = Modifier.size(logoIconSize),
                        tint = tokens.colors.primary
                    )
                }

                Spacer(modifier = Modifier.height(if (isVeryCompactHeight) 6.dp else 12.dp))

                Text(
                    text = stringResource(R.string.institutional_title),
                    style = tokens.typography.display.copy(
                        fontSize = displayTitleSize,
                        fontWeight = FontWeight.Black,
                        letterSpacing = if (isCompactHeight) 2.sp else 3.sp
                    ),
                    color = tokens.colors.textPrimary,
                    textAlign = TextAlign.Center
                )

                if (!isVeryCompactHeight) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.institutional_desc),
                        style = tokens.typography.body.copy(
                            fontSize = displaySubtitleSize,
                            lineHeight = displaySubtitleSize * 1.3f
                        ),
                        color = tokens.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        maxLines = if (isCompactHeight) 2 else 3
                    )
                }
            }

            Spacer(modifier = Modifier.width(gapWidth))

            // --- LADO DIREITO: PAINEL DE LOGIN (VIDRO CINEMÁTICO ADAPTATIVO) ---
            Surface(
                modifier = Modifier
                    .weight(1.1f)
                    .widthIn(min = 340.dp, max = formMaxWidth)
                    .shadow(
                        elevation = 24.dp,
                        shape = tokens.shapes.extraLarge,
                        ambientColor = tokens.colors.primary.copy(alpha = 0.25f),
                        spotColor = tokens.colors.primary.copy(alpha = 0.45f)
                    )
                    .border(
                        BorderStroke(
                            1.5.dp,
                            Brush.linearGradient(
                                colors = listOf(
                                    tokens.colors.primary.copy(alpha = 0.55f),
                                    tokens.colors.border.copy(alpha = 0.25f),
                                    tokens.colors.secondary.copy(alpha = 0.4f)
                                )
                            )
                        ),
                        tokens.shapes.extraLarge
                    ),
                shape = tokens.shapes.extraLarge,
                color = Color(0xFF0D1322).copy(alpha = 0.82f)
            ) {
                Column(
                    modifier = Modifier.padding(formPadding),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = stringResource(R.string.login_welcome_back),
                        style = tokens.typography.headline.copy(
                            fontSize = if (isVeryCompactHeight) 18.sp else if (isCompactHeight) 20.sp else 24.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = tokens.colors.textPrimary
                    )

                    Text(
                        text = stringResource(R.string.login_subtitle_instruction),
                        style = tokens.typography.body.copy(
                            fontSize = if (isVeryCompactHeight) 11.sp else 13.sp
                        ),
                        color = tokens.colors.textSecondary,
                        fontWeight = FontWeight.Medium
                    )

                    // SAVED ACCOUNTS SELECTOR
                    if (savedAccounts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "CONTAS SALVAS",
                            style = tokens.typography.label.copy(fontSize = 11.sp),
                            color = tokens.colors.primary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(savedAccounts) { index, account ->
                                SavedAccountChip(
                                    account = account,
                                    isSelected = username == account.username && (url.isEmpty() || url == account.baseUrl),
                                    onSelect = { onSelectSavedAccount(account) },
                                    onDelete = { onRemoveSavedAccount(index) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(formSpacer))

                    // FIELDS WITH CYAN GLOW
                    AppTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        placeholder = stringResource(R.string.username_label).uppercase(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = inputMinHeight)
                            .focusRequester(usernameFocusRequester),
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Person,
                                null,
                                tint = tokens.colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        imeAction = ImeAction.Next,
                        keyboardActions = KeyboardActions(onNext = { passwordFocusRequester.requestFocus() }),
                        requireClickToEdit = true
                    )

                    Spacer(modifier = Modifier.height(if (isVeryCompactHeight) 6.dp else 10.dp))

                    AppTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        placeholder = stringResource(R.string.password_label).uppercase(),
                        isPassword = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = inputMinHeight)
                            .focusRequester(passwordFocusRequester),
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Lock,
                                null,
                                tint = tokens.colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        imeAction = ImeAction.Go,
                        keyboardActions = KeyboardActions(onGo = { if (!isLoading) onLoginClick() }),
                        requireClickToEdit = true
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = tokens.colors.error.copy(alpha = 0.15f),
                            shape = tokens.shapes.medium,
                            border = BorderStroke(1.dp, tokens.colors.error.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(
                                    elevation = 8.dp,
                                    shape = tokens.shapes.medium,
                                    ambientColor = tokens.colors.error.copy(alpha = 0.3f),
                                    spotColor = tokens.colors.error
                                )
                        ) {
                            Text(
                                text = errorMessage.uppercase(),
                                color = Color(0xFFFF6B6B),
                                style = tokens.typography.caption.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(formSpacer))

                    // BOTÃO ENTRAR CINEMÁTICO COM GRADIENTE E ELEVAÇÃO
                    AppButton(
                        text = if (isLoading) stringResource(R.string.msg_connecting).uppercase() else stringResource(R.string.login_button).uppercase(),
                        onClick = onLoginClick,
                        enabled = !isLoading,
                        useGradient = true,
                        icon = Icons.AutoMirrored.Rounded.ArrowForward,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(buttonHeight)
                    )

                    if (onOpenLocalLogin != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AppButton(
                            text = "LOGIN VIA REDE LOCAL",
                            onClick = onOpenLocalLogin,
                            useGradient = false,
                            icon = Icons.Rounded.Wifi,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(buttonHeight)
                        )
                    }
                }
            }
        }

        // FOOTER
        if (screenHeight >= 420.dp) {
            val context = LocalContext.current
            val appVersion = remember(context) { AppVersionProvider.getFormattedVersionName(context).uppercase() }
            Text(
                text = "SQUI TV • $appVersion PREMIUM",
                style = tokens.typography.caption.copy(fontSize = 11.sp),
                color = tokens.colors.textSecondary.copy(alpha = 0.3f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun SavedAccountChip(
    account: XtreamCredentials,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val tokens = AppDesignSystem

    Surface(
        onClick = onSelect,
        shape = tokens.shapes.medium,
        color = if (isSelected) tokens.colors.primary.copy(alpha = 0.25f) else tokens.colors.surfaceElevated.copy(alpha = 0.6f),
        border = BorderStroke(
            1.dp,
            if (isSelected) tokens.colors.primary else tokens.colors.border.copy(alpha = 0.3f)
        ),
        modifier = Modifier.adaptiveFocus(tokens.shapes.medium)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AccountCircle,
                contentDescription = null,
                tint = if (isSelected) tokens.colors.primary else tokens.colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = account.username.uppercase(),
                    style = tokens.typography.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Black),
                    color = tokens.colors.textPrimary
                )
                Text(
                    text = account.baseUrl.removePrefix("http://").removePrefix("https://").take(18),
                    style = tokens.typography.caption.copy(fontSize = 9.sp),
                    color = tokens.colors.textSecondary
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remover conta",
                    tint = tokens.colors.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
