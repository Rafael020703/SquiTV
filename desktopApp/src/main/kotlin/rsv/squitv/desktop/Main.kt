package rsv.squitv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import java.awt.Dimension
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import rsv.squitv.desktop.data.WindowsCredentialStore
import rsv.squitv.desktop.data.XtreamClient
import rsv.squitv.shared.xtream.ServerCredentials

internal val palette = darkColorScheme(
    primary = Color(0xFF50DED2), onPrimary = Color(0xFF002F2D),
    primaryContainer = Color(0xFF164640), onPrimaryContainer = Color(0xFFA0F2E8),
    background = Color(0xFF0A0E16), surface = Color(0xFF131B28),
    surfaceVariant = Color(0xFF1B2636), onSurface = Color(0xFFE8EDF6),
    onSurfaceVariant = Color(0xFFA8B4C8), outline = Color(0xFF334154))

fun main(args: Array<String>) {
    if (args.size == 2 && args[0] == "--self-check") {
        selfCheck(Path.of(args[1]))
        return
    }
    application {
        val scope = rememberCoroutineScope()
        val directory = remember {
            Path.of(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), DesktopBuildInfo.DATA_FOLDER)
        }
        val controller = remember { DesktopController(XtreamClient(), WindowsCredentialStore(directory), scope) }
        DisposableEffect(controller) { onDispose { controller.close() } }
        Window(onCloseRequest = ::exitApplication, title = DesktopBuildInfo.APP_NAME,
            state = rememberWindowState(width = 1120.dp, height = 760.dp)) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(860, 580) }
            MaterialTheme(colorScheme = palette) {
                Surface(Modifier.fillMaxSize(), color = palette.background) {
                    DesktopApp(controller)
                }
            }
        }
    }
}

@Composable
private fun DesktopApp(controller: DesktopController) {
    val state by controller.state.collectAsState()
    DesktopScreen(state, controller)
}

@Composable
internal fun DesktopScreen(state: DesktopState, controller: DesktopController) {
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Squi TV", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text(if (DesktopBuildInfo.DEBUG) "WINDOWS · DEBUG" else "WINDOWS",
                style = MaterialTheme.typography.labelSmall, color = palette.primary)
            Spacer(Modifier.weight(1f))
            Text(DesktopBuildInfo.VERSION, style = MaterialTheme.typography.labelMedium,
                color = palette.onSurfaceVariant)
            if (state.account != null) {
                Spacer(Modifier.width(20.dp))
                TextButton(onClick = controller::disconnect) { Text("Sair da conta") }
            }
        }
        Spacer(Modifier.height(24.dp))
        if (state.restoring) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        } else if (state.account == null) {
            LoginForm(state, controller, Modifier.weight(1f))
        } else {
            ServerHome(state, controller, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LoginForm(state: DesktopState, controller: DesktopController, modifier: Modifier) {
    var address by remember(state.savedCredentials) { mutableStateOf(state.savedCredentials?.baseUrl.orEmpty()) }
    var username by remember(state.savedCredentials) { mutableStateOf(state.savedCredentials?.username.orEmpty()) }
    var password by remember(state.savedCredentials) { mutableStateOf(state.savedCredentials?.password.orEmpty()) }
    var rememberAccount by remember { mutableStateOf(state.savedCredentials != null) }
    var showPassword by remember { mutableStateOf(false) }
    val connect = { controller.connect(address, username, password, rememberAccount) }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Card(Modifier.widthIn(max = 460.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = palette.surface)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Entre na sua conta", style = MaterialTheme.typography.headlineSmall)
                Text("Use os dados do seu servidor para acessar o catálogo.",
                    style = MaterialTheme.typography.bodyMedium, color = palette.onSurfaceVariant)
                OutlinedTextField(address, { address = it }, label = { Text("Endereço do servidor") },
                    placeholder = { Text("http://servidor:porta") }, enabled = !state.busy,
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                OutlinedTextField(username, { username = it }, label = { Text("Usuário") },
                    enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                OutlinedTextField(password, { password = it }, label = { Text("Senha") }, enabled = !state.busy,
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) "Ocultar" else "Mostrar", style = MaterialTheme.typography.labelSmall)
                    } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!state.busy) connect() }))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(rememberAccount, { rememberAccount = it }, enabled = !state.busy)
                    Text("Lembrar minha conta neste computador", style = MaterialTheme.typography.bodySmall)
                }
                state.error?.let { Text(it, color = palette.error, style = MaterialTheme.typography.bodySmall) }
                state.notice?.let { Text(it, color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = connect, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Conectar")
                }
                if (state.busy) TextButton(onClick = controller::cancelConnection,
                    modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancelar") }
            }
        }
    }
}

@Composable
private fun ServerHome(state: DesktopState, controller: DesktopController, modifier: Modifier) {
    val account = state.account ?: return
    var search by remember(state.selectedCategory?.id) { mutableStateOf("") }
    val channels = remember(state.channels, search) {
        if (search.isBlank()) state.channels else state.channels.filter { it.name.contains(search.trim(), ignoreCase = true) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountCard("Conta ativa", account.username, Modifier.weight(1f))
            AccountCard("Validade", account.expiresAt?.let {
                Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            } ?: "Não informada", Modifier.weight(1f))
            AccountCard("Conexões", "${account.activeConnections ?: "—"} / ${account.maxConnections ?: "—"}",
                Modifier.weight(1f))
        }
        Text(state.credentials?.baseUrl.orEmpty(), style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
        account.timezone?.let {
            Text("Fuso do servidor: $it", style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
        }
        state.notice?.let { Text(it, color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        state.error?.let { Text(it, color = palette.error, style = MaterialTheme.typography.bodySmall) }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Card(Modifier.width(250.dp).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = palette.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ao vivo", style = MaterialTheme.typography.titleMedium)
                    Text("${state.categories.size} categorias", style = MaterialTheme.typography.bodySmall,
                        color = palette.onSurfaceVariant)
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (!state.busy && state.categories.isEmpty()) {
                        Text("Nenhuma categoria disponível.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            state.credentials?.let { controller.connect(it.baseUrl, it.username, it.password, state.savedCredentials != null) }
                        }) { Text("Tentar novamente") }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(state.categories, key = { it.id }) { category ->
                            val selected = category.id == state.selectedCategory?.id
                            FilledTonalButton(onClick = { controller.loadChannels(category) }, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (selected) palette.primaryContainer else Color.Transparent,
                                    contentColor = if (selected) palette.onPrimaryContainer else palette.onSurface)) {
                                Text(category.name, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.selectedCategory?.name ?: "Canais ao vivo", style = MaterialTheme.typography.titleLarge)
                if (state.selectedCategory != null) OutlinedTextField(search, { search = it },
                    placeholder = { Text("Buscar nesta categoria") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                when {
                    state.loadingChannels -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.channelError != null -> {
                        Text(state.channelError, color = palette.error)
                        state.selectedCategory?.let { category ->
                            TextButton(onClick = { controller.loadChannels(category) }) { Text("Tentar novamente") }
                        }
                    }
                    state.selectedCategory == null -> Text("Selecione uma categoria para carregar os canais.",
                        color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    channels.isEmpty() -> Text("Nenhum canal encontrado.", color = palette.onSurfaceVariant)
                    else -> {
                        Text("${channels.size} canais", style = MaterialTheme.typography.labelMedium, color = palette.onSurfaceVariant)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(channels, key = { it.id }) { channel ->
                                Surface(shape = MaterialTheme.shapes.medium, color = palette.surface) {
                                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(channel.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                        Text("AO VIVO", style = MaterialTheme.typography.labelSmall, color = palette.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = palette.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = palette.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun selfCheck(report: Path) {
    val directory = Files.createTempDirectory("squitv-windows-check-")
    try {
        val store = WindowsCredentialStore(directory)
        val fixture = ServerCredentials("https://example.invalid/", "fixture", "not-a-real-password")
        store.save(fixture)
        check(store.load() == fixture)
        check(!Files.readAllBytes(directory.resolve("account.dpapi")).toString(Charsets.ISO_8859_1).contains(fixture.password))
        store.clear()
        check(store.load() == null)
        check(rsv.squitv.shared.update.VersionComparator.compareVersions("1.21", "1.21.0") == 0)
        Files.writeString(report, """{"ok":true,"variant":"${if (DesktopBuildInfo.DEBUG) "debug" else "release"}","version":"${DesktopBuildInfo.VERSION}","dataFolder":"${DesktopBuildInfo.DATA_FOLDER}","dpapi":true}""")
    } finally { Files.deleteIfExists(directory.resolve("account.dpapi")); Files.deleteIfExists(directory) }
}
