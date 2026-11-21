package rsv.squitv.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import rsv.squitv.shared.account.AccountPilot

enum class AccountLoginText(val portuguese: String) {
    SIGN_IN("Entrar com Conta Squi TV"),
    GENERATING("Gerando código…"),
    CONFIRM_CODE("Abra o endereço em um navegador e confirme o código."),
    AUTH_EXPIRED("A autorização expirou ou foi recusada. Feche e tente novamente."),
    CONNECT_FAILURE("Falha ao conectar à Conta Squi TV."),
    ACCOUNT("Conta Squi TV"),
    PHONE_CODE("Abra o endereço no celular e informe o código."),
    OPEN_BROWSER("Abrir navegador"),
    CLOSE("Fechar"),
    SIGN_IN_FAILURE("Falha ao entrar na Conta Squi TV."),
    PORTAL_HINT("Use o usuário e a senha que você cadastrou no portal Squi TV."),
    USERNAME("Usuário da Conta Squi TV"),
    PASSWORD("Senha da Conta Squi TV"),
    HIDE_PASSWORD("Ocultar senha"),
    SHOW_PASSWORD("Mostrar senha"),
    SIGN_IN_CODE("Entrar com código"),
    SIGNING_IN("Entrando…"),
    ENTER("Entrar"),
    CANCEL("Cancelar"),
    CHOOSE_LINK("Escolher vínculo IPTV"),
    LINK_FAILURE("Não foi possível abrir o vínculo."),
    LINK("Vínculo %1\$d · %2\$s"),
    LOADING_LINK("Carregando vínculo…"),
    SIGN_IN_AGAIN("Entrar novamente"),
    INVALID_CREDENTIALS("Usuário ou senha da Conta Squi TV inválidos."),
    TOO_MANY_ATTEMPTS("Muitas tentativas. Aguarde e tente novamente."),
    UNAVAILABLE("O login da Conta Squi TV está temporariamente indisponível."),
    NO_IPTV_LINK("Vincule sua conta IPTV no portal Squi TV antes de entrar."),
    LINK_EXPIRED("A seleção do vínculo expirou. Entre novamente."),
}

@Composable
fun AccountPilotEntry(
    enabled: Boolean,
    copy: (AccountLoginText) -> String = { it.portuguese },
    onLinkedLogin: (AccountPilot.LinkedLogin) -> Unit,
) {
    var directLogin by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<AccountPilot.LoginResult.Choose?>(null) }
    var showing by remember { mutableStateOf(false) }
    val onResult: (AccountPilot.LoginResult) -> Unit = { result ->
        directLogin = false
        showing = false
        when (result) {
            is AccountPilot.LoginResult.Connected -> onLinkedLogin(result.credentials)
            is AccountPilot.LoginResult.Choose -> selection = result
        }
    }
    selection?.let { choices ->
        SelectIptvLink(copy, choices, { selection = null }, onSignInAgain = {
            selection = null
            directLogin = true
        }) { credentials ->
            selection = null
            onLinkedLogin(credentials)
        }
    }
    OutlinedButton({ directLogin = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(copy(AccountLoginText.SIGN_IN))
    }
    if (directLogin) {
        DirectAccountLogin(
            copy = copy,
            onDismiss = { directLogin = false },
            onUseCode = { directLogin = false; showing = true },
            onLinkedLogin = onResult,
        )
    }
    if (showing) {
        var authorization by remember { mutableStateOf<AccountPilot.Authorization?>(null) }
        var message by remember { mutableStateOf(copy(AccountLoginText.GENERATING)) }
        var finished by remember { mutableStateOf(false) }
        val browser = LocalUriHandler.current
        val connect by rememberUpdatedState(onResult)
        LaunchedEffect(Unit) {
            try {
                val grant = AccountPilot.begin()
                authorization = grant
                message = copy(AccountLoginText.CONFIRM_CODE)
                val linked = AccountPilot.awaitLinkedLogin(grant)
                if (linked == null) {
                    message = copy(AccountLoginText.AUTH_EXPIRED)
                } else {
                    connect(linked)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = copy(AccountLoginText.CONNECT_FAILURE)
            }
            finished = true
        }
        AlertDialog(
            onDismissRequest = { showing = false },
            title = { Text(copy(AccountLoginText.ACCOUNT)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(message)
                    if (!finished) authorization?.let {
                        SelectionContainer { Text(AccountPilot.verificationPage) }
                        SelectionContainer { Text(it.userCode, style = MaterialTheme.typography.headlineSmall) }
                    }
                }
            },
            confirmButton = {
                if (!finished) authorization?.let { grant ->
                    TextButton({
                        runCatching { browser.openUri(grant.browserUrl) }.onFailure {
                            message = copy(AccountLoginText.PHONE_CODE)
                        }
                    }) { Text(copy(AccountLoginText.OPEN_BROWSER)) }
                }
            },
            dismissButton = { TextButton({ showing = false }) { Text(copy(AccountLoginText.CLOSE)) } }
        )
    }
}

@Composable
private fun DirectAccountLogin(
    copy: (AccountLoginText) -> String,
    onDismiss: () -> Unit,
    onUseCode: () -> Unit,
    onLinkedLogin: (AccountPilot.LoginResult) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val connect by rememberUpdatedState(onLinkedLogin)
    val canSubmit = !busy && username.isNotBlank() && password.isNotBlank()

    fun submit() {
        if (busy || !canSubmit) return
        busy = true
        error = null
        scope.launch {
            try {
                connect(AccountPilot.login(username.trim(), password))
                password = ""
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                error = copy(when ((exception as? AccountPilot.LoginRejected)?.reason) {
                    AccountPilot.LoginFailure.INVALID_CREDENTIALS -> AccountLoginText.INVALID_CREDENTIALS
                    AccountPilot.LoginFailure.TOO_MANY_ATTEMPTS -> AccountLoginText.TOO_MANY_ATTEMPTS
                    AccountPilot.LoginFailure.UNAVAILABLE -> AccountLoginText.UNAVAILABLE
                    AccountPilot.LoginFailure.NO_IPTV_LINK -> AccountLoginText.NO_IPTV_LINK
                    null -> AccountLoginText.SIGN_IN_FAILURE
                })
            } finally {
                passwordVisible = false
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(copy(AccountLoginText.SIGN_IN)) },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(copy(AccountLoginText.PORTAL_HINT),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = username,
                    onValueChange = { if (it.length <= 256) { username = it; error = null } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(copy(AccountLoginText.USERNAME)) },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { if (it.length <= 256) { password = it; error = null } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(copy(AccountLoginText.PASSWORD)) },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !busy) {
                            Icon(if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (passwordVisible) copy(AccountLoginText.HIDE_PASSWORD) else copy(AccountLoginText.SHOW_PASSWORD))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onUseCode, enabled = !busy) { Text(copy(AccountLoginText.SIGN_IN_CODE)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = ::submit,
            ) { Text(if (busy) copy(AccountLoginText.SIGNING_IN) else copy(AccountLoginText.ENTER)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(copy(AccountLoginText.CANCEL)) } },
    )
}
@Composable
private fun SelectIptvLink(
    copy: (AccountLoginText) -> String,
    selection: AccountPilot.LoginResult.Choose,
    onDismiss: () -> Unit,
    onSignInAgain: () -> Unit,
    onLinkedLogin: (AccountPilot.LinkedLogin) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val connect by rememberUpdatedState(onLinkedLogin)
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var expired by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(copy(AccountLoginText.CHOOSE_LINK)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (choice in selection.choices) {
                    OutlinedButton(
                        enabled = !busy && !expired,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            busy = true
                            error = null
                            scope.launch {
                                try {
                                    connect(AccountPilot.selectLink(selection, choice.slot))
                                } catch (exception: CancellationException) {
                                    throw exception
                                } catch (exception: AccountPilot.LinkSelectionExpired) {
                                    expired = true
                                    error = copy(AccountLoginText.LINK_EXPIRED)
                                } catch (exception: Exception) {
                                    error = copy(AccountLoginText.LINK_FAILURE)
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    ) { Text(copy(AccountLoginText.LINK).format(choice.slot, choice.username)) }
                }
                if (busy) Text(copy(AccountLoginText.LOADING_LINK))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { if (expired) TextButton(onClick = onSignInAgain) { Text(copy(AccountLoginText.SIGN_IN_AGAIN)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(copy(AccountLoginText.CANCEL)) } },
    )
}
