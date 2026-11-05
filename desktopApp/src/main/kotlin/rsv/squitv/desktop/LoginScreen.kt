package rsv.squitv.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URI

@Composable
internal fun LoginForm(state: DesktopState, controller: DesktopController, modifier: Modifier) {
    val saved = state.savedCredentials ?: state.debugLoginDefaults
    var address by rememberSaveable(saved) {
        mutableStateOf(saved?.baseUrl?.takeUnless { it in controller.availableDns }.orEmpty())
    }
    var username by rememberSaveable(saved) { mutableStateOf(saved?.username.orEmpty()) }
    var password by rememberSaveable(saved) { mutableStateOf(saved?.password.orEmpty()) }
    var rememberAccount by rememberSaveable(saved) { mutableStateOf(saved != null) }
    var showPassword by remember { mutableStateOf(false) }
    var customAddress by rememberSaveable(saved) { mutableStateOf(address.isNotEmpty()) }
    var dnsExpanded by remember { mutableStateOf(false) }
    val windowDragArea = LocalWindowDragArea.current
    val formScroll = rememberScrollState()
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = palette.primary, unfocusedBorderColor = palette.onSurface.copy(alpha = .12f),
        focusedContainerColor = palette.primary.copy(alpha = .12f),
        unfocusedContainerColor = palette.onSurface.copy(alpha = .05f),
        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
        focusedLeadingIconColor = palette.primary, unfocusedLeadingIconColor = palette.onSurfaceVariant)
    val connect = { controller.connect(if (customAddress) address else "", username, password, rememberAccount) }
    Box(modifier.fillMaxWidth()) {
        PortalAtmosphere(Modifier.matchParentSize())
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(44.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            windowDragArea(Modifier.weight(1f, fill = false).widthIn(max = 460.dp).fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = RoundedCornerShape(22.dp), color = palette.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, palette.primary.copy(alpha = 0.6f))) {
                    Image(brandPainter(), "Squi TV", Modifier.size(88.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("TV AO VIVO", fontSize = 38.sp, fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp, textAlign = TextAlign.Center, color = palette.onSurface)
                Spacer(Modifier.height(8.dp))
                Text("Canais, filmes, séries e muito mais.\nTudo em um só lugar.",
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                    color = palette.onSurfaceVariant)
            }
            }
            Surface(
                Modifier.weight(1.1f, fill = false).widthIn(max = 480.dp).fillMaxWidth().padding(vertical = 12.dp),
                shape = RoundedCornerShape(24.dp), color = Color(0xFF0D1322).copy(alpha = 0.88f),
                border = BorderStroke(1.dp, Brush.linearGradient(listOf(
                    palette.primary.copy(alpha = 0.55f), palette.outline.copy(alpha = 0.35f),
                    palette.secondary.copy(alpha = 0.4f))))
            ) {
                Box {
                Column(Modifier.verticalScroll(formScroll).padding(26.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("BEM-VINDO DE VOLTA!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("Faça seu login para continuar assistindo.", style = MaterialTheme.typography.bodySmall,
                        color = palette.onSurfaceVariant)
                    OutlinedTextField(username, { username = it }, label = { Text("USUÁRIO") },
                        leadingIcon = { Icon(Icons.Rounded.Person, null, Modifier.size(20.dp)) },
                        shape = RoundedCornerShape(16.dp), colors = fieldColors,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                    OutlinedTextField(password, { password = it }, label = { Text("SENHA") },
                        leadingIcon = { Icon(Icons.Rounded.Lock, null, Modifier.size(20.dp)) },
                        shape = RoundedCornerShape(16.dp), colors = fieldColors,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                if (showPassword) "Ocultar senha" else "Mostrar senha", Modifier.size(20.dp), tint = palette.onSurfaceVariant)
                        } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (!state.busy) connect() }))
                    HorizontalDivider(color = palette.outline.copy(alpha = 0.4f))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("SERVIDOR DNS", style = MaterialTheme.typography.labelSmall, color = palette.primary,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = controller::testDns, enabled = !state.testingDns) {
                            Icon(Icons.Rounded.NetworkCheck, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.testingDns) "Verificando…" else "Testar DNS",
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Box {
                        OutlinedButton(onClick = { dnsExpanded = true }, enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Rounded.Dns, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(if (customAddress) "Endereço personalizado"
                                else state.selectedDns?.let { host(it) } ?: "Automático · melhor disponível",
                                modifier = Modifier.weight(1f), textAlign = TextAlign.Start,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Rounded.ExpandMore, null, Modifier.size(20.dp))
                        }
                        DropdownMenu(expanded = dnsExpanded, onDismissRequest = { dnsExpanded = false }) {
                            DropdownMenuItem(text = { Text("Automático · melhor disponível") }, onClick = {
                                controller.selectDns(null); customAddress = false; dnsExpanded = false
                            })
                            controller.availableDns.forEach { dns ->
                                val latency = state.dnsStatus[dns]
                                DropdownMenuItem(text = {
                                    Row(Modifier.widthIn(min = 270.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(host(dns))
                                        Text(when {
                                            latency == null -> "Aguardando"
                                            latency < 0 -> "Sem resposta"
                                            else -> "$latency ms"
                                        }, color = if (latency != null && latency >= 0) palette.primary else palette.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                }, onClick = {
                                    controller.selectDns(dns); customAddress = false; dnsExpanded = false
                                })
                            }
                            DropdownMenuItem(text = { Text("Endereço personalizado") }, onClick = {
                                customAddress = true; dnsExpanded = false
                            })
                        }
                    }
                    if (customAddress) OutlinedTextField(address, { address = it },
                        label = { Text("Endereço do servidor") }, placeholder = { Text("http://servidor:porta") },
                        leadingIcon = { Icon(Icons.Rounded.Language, null, Modifier.size(20.dp)) },
                        shape = RoundedCornerShape(16.dp), colors = fieldColors,
                        enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(rememberAccount, { rememberAccount = it }, enabled = !state.busy)
                        Text("Lembrar minha conta", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(controller::openAppDns, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Dns, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("DNS do aplicativo")
                    }
                    state.error?.let { StatusNotice(it, error = true) }
                    state.notice?.let { StatusNotice(it) }
                    state.connectingServer?.let {
                        Text("Conectando a ${host(it)}…", color = palette.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = connect, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp)) {
                        if (state.busy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = palette.onSurface)
                            Spacer(Modifier.width(10.dp))
                            Text("CONECTANDO…", color = palette.onSurface, style = MaterialTheme.typography.labelMedium)
                        } else Text("ENTRAR", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    if (state.busy) TextButton(onClick = controller::cancelConnection,
                        modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancelar") }
                    if (!state.busy && state.savedCredentials != null) TextButton(onClick = controller::openDownloads,
                        modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Downloads salvos") }
                    Text("SQUI TV • ${DesktopBuildInfo.VERSION}${if (DesktopBuildInfo.DEBUG) " DEBUG" else ""}",
                        style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                if (formScroll.canScrollBackward || formScroll.canScrollForward)
                    VerticalScrollbar(rememberScrollbarAdapter(formScroll), style = desktopScrollbarStyle(),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 16.dp, horizontal = 6.dp))
                }
            }
        }
    }
}

private fun host(url: String) = runCatching { URI(url).authority }.getOrNull() ?: "Servidor"

@Composable
private fun PortalAtmosphere(modifier: Modifier) {
    Canvas(modifier.background(palette.background)) {
        drawRect(Brush.radialGradient(listOf(palette.primary.copy(alpha = 0.08f), Color.Transparent),
            center = Offset(size.width * 0.2f, size.height * 0.4f), radius = size.width * 0.75f))
        val step = 24.dp.toPx()
        var x = 0f
        while (x < size.width) {
            var y = 0f
            while (y < size.height) {
                drawCircle(Color.White.copy(alpha = 0.02f), 0.6.dp.toPx(), Offset(x, y))
                y += step
            }
            x += step
        }
    }
}
