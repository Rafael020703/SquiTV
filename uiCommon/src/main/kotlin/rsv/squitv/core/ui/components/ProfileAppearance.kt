package rsv.squitv.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import rsv.squitv.shared.account.ProfileAvatar

data class ProfileCardEntry(val id: String, val name: String, val avatar: String, val detail: String? = null)

@Composable
fun ProfilePickerDialog(
    profiles: List<ProfileCardEntry>,
    activeId: String?,
    busy: Boolean,
    message: String?,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit,
    onCreate: (() -> Unit)?,
    onDismiss: () -> Unit,
    photo: @Composable (String) -> ImageBitmap?,
) {
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.widthIn(max = 650.dp).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Quem está assistindo?", style = MaterialTheme.typography.headlineSmall)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                val focus = remember { FocusRequester() }
                val grid = rememberLazyGridState()
                val firstId = profiles.firstOrNull { it.id == activeId }?.id ?: profiles.firstOrNull()?.id
                if (profiles.isNotEmpty()) LazyVerticalGrid(
                    GridCells.Adaptive(110.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 345.dp),
                    state = grid,
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ProfileAvatarBadge(profile.avatar, profile.name,
                                Modifier.size(66.dp).then(if (profile.id == firstId) Modifier.focusRequester(focus) else Modifier),
                                photo(profile.avatar), enabled = !busy,
                                onClick = { onSelect(profile.id) })
                            Text(profile.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            val detail = if (profile.id == activeId) "Em uso" else profile.detail
                            detail?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                            TextButton({ onEdit(profile.id) }, enabled = !busy) { Text("Editar") }
                        }
                    }
                }
                LaunchedEffect(firstId, busy) {
                    if (firstId != null && !busy) {
                        val index = profiles.indexOfFirst { it.id == firstId }
                        if (index >= 0 && grid.layoutInfo.visibleItemsInfo.none { it.index == index }) grid.scrollToItem(index)
                        withFrameNanos { }
                        runCatching { focus.requestFocus() }
                    }
                }
                Row {
                    onCreate?.let { TextButton(it, enabled = !busy) { Text("Novo perfil") } }
                    TextButton(onDismiss, enabled = !busy) { Text("Fechar") }
                }
            }
        }
    }
}

@Composable
fun ProfileAvatarBadge(avatarKey: String?, name: String, modifier: Modifier = Modifier,
    photo: ImageBitmap? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val avatar = ProfileAvatar.fromKey(avatarKey)
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.then(
        Modifier.semantics { contentDescription = name.ifBlank { "Perfis" } }),
        shape = CircleShape, color = Color(avatar.background)) {
        Box(contentAlignment = Alignment.Center) {
            if (photo != null) Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(if (avatar == ProfileAvatar.DEFAULT) name.trim().take(1).uppercase().ifBlank { "●" } else avatar.symbol,
                    color = Color.White, fontSize = 24.sp)
        }
    }
}

@Composable
fun ProfileAppearanceDialog(name: String, avatarKey: String?, onDismiss: () -> Unit,
    photo: @Composable (String) -> ImageBitmap? = { null },
    onSave: suspend (String, String) -> Unit) {
    var draft by remember { mutableStateOf(name) }
    val hasPhoto = remember(avatarKey) { ProfileAvatar.photoBytes(avatarKey) != null }
    var avatar by remember { mutableStateOf(if (hasPhoto) null else ProfileAvatar.fromKey(avatarKey)) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Personalizar perfil") },
        text = {
            Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(draft, { if (it.length <= 80) draft = it }, enabled = !saving,
                    singleLine = true, label = { Text("Nome do perfil") })
                if (hasPhoto) FilterChip(selected = avatar == null, onClick = { avatar = null }, enabled = !saving,
                    label = { Text("Foto do perfil") }, leadingIcon = {
                        avatarKey?.let { key -> photo(key)?.let { Image(it, null, Modifier.size(24.dp), contentScale = ContentScale.Crop) } }
                    })
                ProfileAvatar.entries.chunked(2).forEach { options ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        options.forEach { option ->
                            val picture = photo(option.key)
                            FilterChip(selected = avatar == option, onClick = { avatar = option }, enabled = !saving,
                                label = { Text(option.label) }, modifier = Modifier.weight(1f), leadingIcon = {
                                    if (picture != null) Image(picture, null, Modifier.size(24.dp), contentScale = ContentScale.Crop)
                                    else Text(option.symbol)
                                })
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving && draft.isNotBlank(), onClick = {
                saving = true
                error = null
                scope.launch {
                    try {
                        onSave(draft.trim(), avatar?.key ?: avatarKey ?: ProfileAvatar.DEFAULT.key)
                        onDismiss()
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (failure: Exception) {
                        error = failure.message ?: "Não foi possível salvar o perfil."
                    } finally {
                        saving = false
                    }
                }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancelar") } },
    )
}
