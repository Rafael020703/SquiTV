package rsv.squitv.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import rsv.squitv.shared.account.CloudProfile
import rsv.squitv.shared.account.FavoriteCloudClient
import rsv.squitv.shared.account.FavoriteLocalStore

@Composable
fun AccountSyncSettingsDialog(
    client: FavoriteCloudClient?,
    local: FavoriteLocalStore?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (client == null || local == null) {
        AlertDialog(
            onDismissRequest = onClose,
            modifier = modifier,
            title = { Text("Sincronizar conta") },
            text = { Text("Entre com a Conta Squi TV para sincronizar favoritos, progresso e organização das categorias.") },
            confirmButton = { TextButton(onClick = onClose) { Text("Fechar") } },
        )
    } else {
        key(client, local) { FavoriteCloudDialog(client, local, onClose, modifier) }
    }
}

@Composable
private fun FavoriteCloudDialog(
    client: FavoriteCloudClient,
    local: FavoriteLocalStore,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf<List<CloudProfile>>(emptyList()) }
    var selected by remember { mutableStateOf<CloudProfile?>(null) }
    var boundId by remember { mutableStateOf<String?>(null) }
    var profileName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    fun runAction(action: suspend () -> Unit) {
        busy = true
        error = null
        success = null
        scope.launch {
            try {
                action()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                error = failure.message ?: "Não foi possível sincronizar a conta."
            } finally {
                busy = false
            }
        }
    }

    suspend fun syncProfile(profile: CloudProfile) {
        val favoriteConflicts = client.sync(profile, local, allowImport = boundId == null)
        boundId = profile.id
        val completed = mutableListOf("Favoritos")
        val failures = mutableListOf<String>()
        var progressEmpty = false
        try {
            val progressCount = client.syncProgress(profile, local)
            progressEmpty = progressCount == 0
            if (!progressEmpty) completed += "progresso ($progressCount)"
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            failures += "Progresso · ${failure.message ?: "Falha na sincronização."}"
        }
        try {
            if (client.reconcileCategories(profile, local) == 0) completed += "categorias"
            else failures += "Categorias · Há alterações no painel e neste aparelho. Escolha qual organização aplicar."
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            failures += "Categorias · ${failure.message ?: "Falha na sincronização."}"
        }
        success = completed.joinToString() + " — sincronização concluída."
        if (progressEmpty) success += " Ainda não há progresso novo no perfil. O histórico anterior à associação fica no aparelho."
        if (favoriteConflicts > 0) success += " Nos favoritos em conflito, prevaleceram as alterações do painel."
        error = failures.joinToString("\n").ifEmpty { null }
    }

    fun loadProfiles() {
        runAction {
            boundId = local.checkpoint()?.profile
            profiles = client.profiles()
            selected = profiles.firstOrNull { it.id == boundId }
            if (boundId != null && selected == null) {
                error = "O perfil associado não está disponível na Conta Squi TV."
            }
            loaded = true
            selected?.let { syncProfile(it) }
        }
    }

    LaunchedEffect(Unit) { loadProfiles() }

    AlertDialog(
        onDismissRequest = onClose,
        modifier = modifier,
        title = { Text("Sincronizar conta") },
        text = {
            Column(
                Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text("Lista local · ${local.label}")
                if (busy) Text("Aguarde…")
                success?.let { Text(it) }
                if (!loaded && !busy) {
                    TextButton(onClick = { loadProfiles() }) { Text("Tentar novamente") }
                }
                if (loaded && !busy && boundId == null) {
                    Text("Escolha um perfil para importar os favoritos locais uma vez e sincronizar a organização. O histórico antigo fica neste aparelho.")
                    for (profile in profiles) {
                        TextButton(onClick = { selected = profile }) {
                            Text(if (selected?.id == profile.id) "✓ ${profile.name}" else profile.name)
                        }
                    }
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { if (it.length <= 80) profileName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Novo perfil") },
                    )
                    TextButton(
                        enabled = profileName.isNotBlank(),
                        onClick = {
                            runAction {
                                val profile = client.createProfile(profileName.trim())
                                profiles = profiles + profile
                                selected = profile
                                profileName = ""
                            }
                        },
                    ) { Text("Criar perfil") }
                } else if (loaded && !busy) {
                    selected?.let { profile ->
                        Text("Perfil da Conta Squi TV · ${profile.name}")
                        TextButton(onClick = {
                            runAction {
                                client.sendCategories(profile, local)
                                success = "Organização deste aparelho enviada ao painel."
                            }
                        }) { Text("Enviar organização ao painel") }
                        TextButton(onClick = {
                            runAction {
                                client.receiveCategories(profile, local)
                                success = "Organização do painel aplicada neste aparelho."
                            }
                        }) { Text("Aplicar organização do painel") }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = loaded && !busy && selected != null,
                onClick = {
                    val profile = selected ?: return@TextButton
                    runAction { syncProfile(profile) }
                },
            ) { Text(if (boundId == null) "Importar e sincronizar" else "Sincronizar agora") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Fechar") } },
    )
}
