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
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import rsv.squitv.shared.account.AccountPilot

@Composable
fun AccountPilotEntry(enabled: Boolean, onLinkedLogin: (AccountPilot.LinkedLogin) -> Unit) {
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
        SelectIptvLink(choices, { selection = null }, onSignInAgain = {
            selection = null
            directLogin = true
        }) { credentials ->
            selection = null
            onLinkedLogin(credentials)
        }
    }
    OutlinedButton({ directLogin = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text("Entrar com Conta Squi TV")
    }
    if (directLogin) {
        DirectAccountLogin(
            onDismiss = { directLogin = false },
            onUseCode = { directLogin = false; showing = true },
            onLinkedLogin = onResult,
        )
    }
    if (showing) {
        var authorization by remember { mutableStateOf<AccountPilot.Authorization?>(null) }
        var message by remember { mutableStateOf("Gerando código…") }
        var finished by remember { mutableStateOf(false) }
        val browser = LocalUriHandler.current
        val connect by rememberUpdatedState(onResult)
        LaunchedEffect(Unit) {
            try {
                val grant = AccountPilot.begin()
                authorization = grant
                message = "Abra o endereço em um navegador e confirme o código."
                val linked = AccountPilot.awaitLinkedLogin(grant)
                if (linked == null) {
                    message = "A autorização expirou ou foi recusada. Feche e tente novamente."
                } else {
                    connect(linked)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message ?: "Falha ao conectar à Conta Squi TV."
            }
            finished = true
        }
        AlertDialog(
            onDismissRequest = { showing = false },
            title = { Text("Conta Squi TV") },
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
                            message = "Abra o endereço no celular e informe o código."
                        }
                    }) { Text("Abrir navegador") }
                }
            },
            dismissButton = { TextButton({ showing = false }) { Text("Fechar") } }
        )
    }
}

@Composable
private fun DirectAccountLogin(
    onDismiss: () -> Unit,
    onUseCode: () -> Unit,
    onLinkedLogin: (AccountPilot.LoginResult) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
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
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                error = exception.message ?: "Falha ao entrar na Conta Squi TV."
            } finally {
                password = ""
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Entrar com Conta Squi TV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { if (it.length <= 256) { username = it; error = null } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Usuário da Conta Squi TV") },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { if (it.length <= 256) { password = it; error = null } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Senha da Conta Squi TV") },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onUseCode, enabled = !busy) { Text("Entrar com código") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = ::submit,
            ) { Text(if (busy) "Entrando…" else "Entrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
@Composable
private fun SelectIptvLink(
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
        title = { Text("Escolher vínculo IPTV") },
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
                                    error = exception.message
                                } catch (exception: Exception) {
                                    error = exception.message ?: "Não foi possível abrir o vínculo."
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    ) { Text("Vínculo ${choice.slot} · ${choice.username}") }
                }
                if (busy) Text("Carregando vínculo…")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { if (expired) TextButton(onClick = onSignInAgain) { Text("Entrar novamente") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
