package rsv.squitv.ui.content

import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.hilt.navigation.compose.hiltViewModel
import rsv.squitv.R
import rsv.squitv.domain.model.ContentType
import rsv.squitv.core.ui.components.navigation.AppHeader
import rsv.squitv.core.ui.components.states.LoadingState
import rsv.squitv.core.ui.components.states.EmptyState
import rsv.squitv.core.ui.components.buttons.AppIconButton
import rsv.squitv.core.ui.theme.*
import rsv.squitv.ui.dashboard.PortalBackground
import rsv.squitv.ui.viewmodel.library.LibraryViewModel
import rsv.squitv.util.rememberWindowInfo
import rsv.squitv.ui.dashboard.ContentRow
import rsv.squitv.ui.viewmodel.CategoryViewModel

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ActorDetailScreen(
    actorName: String,
    categoryViewModel: CategoryViewModel,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onSeriesClick: (Int, String, String?) -> Unit,
    onVodClick: (Int, String, String?) -> Unit
) {
    val rows by categoryViewModel.currentContentRows.collectAsStateWithLifecycle()
    val watchProgress by libraryViewModel.watchProgress.collectAsStateWithLifecycle()
    val favorites by libraryViewModel.favorites.collectAsStateWithLifecycle()
    val isLoading by categoryViewModel.isLoading.collectAsStateWithLifecycle()
    val windowInfo = rememberWindowInfo()
    val isExpanded = windowInfo.isExpanded
    val tokens = AppDesignSystem
    
    val listState = rememberLazyListState()
    val rowStateHolder = rememberSaveableStateHolder()
    val sanitizedActorName = remember(actorName) { actorName.trim() }
    var showSortMenu by remember { mutableStateOf(false) }

    val avatarColor = remember(sanitizedActorName) { 
        val hash = sanitizedActorName.hashCode()
        val h = (hash and 0xFFFF) % 360
        Color.hsl(h.toFloat(), 0.6f, 0.4f) 
    }

    PortalBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            val totalWorks = rows.sumOf { it.items.size }
            
            AppHeader(
                title = sanitizedActorName,
                subtitle = if (totalWorks > 0) "$totalWorks obras encontradas" else stringResource(R.string.local_filmography),
                actions = {
                    AppIconButton(Icons.AutoMirrored.Rounded.Sort, { showSortMenu = true })
                }
            )

            if (isLoading) {
                LoadingState()
            } else if (rows.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.no_content_for_actor),
                    description = "Este ator não possui conteúdos disponíveis na sua biblioteca no momento."
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 64.dp)
                ) {
                    item {
                        // Actor Intro with Avatar
                        Row(
                            modifier = Modifier.padding(horizontal = tokens.spacing.extraLarge, vertical = tokens.spacing.large),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(if (isExpanded) 100.dp else 80.dp),
                                shape = CircleShape,
                                color = avatarColor,
                                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.2f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = sanitizedActorName.firstOrNull()?.uppercase() ?: "",
                                        style = if (isExpanded) tokens.typography.display else tokens.typography.headline,
                                        color = Color.White,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(tokens.spacing.extraLarge))
                            Text(
                                text = stringResource(R.string.actor_explore_msg, sanitizedActorName),
                                style = tokens.typography.body,
                                color = tokens.colors.textSecondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    items(rows, key = { it.title }) { row ->
                        rowStateHolder.SaveableStateProvider(row.title) {
                        ContentRow(
                            title = row.title,
                            items = row.items,
                            watchProgress = watchProgress,
                            favorites = favorites,
                            onToggleFavorite = libraryViewModel::toggleFavorite,
                            onItemClick = { item ->
                                if (item.type == ContentType.SERIES) onSeriesClick(item.id.toInt(), item.name, item.icon)
                                else onVodClick(item.id.toInt(), item.name, item.icon)
                            }
                        )
                        }
                    }
                }
            }
        }
    }

    if (showSortMenu) {
        AlertDialog(
            onDismissRequest = { showSortMenu = false },
            title = { 
                Text(
                    text = "ORDENAR POR", 
                    style = tokens.typography.title, 
                    fontWeight = FontWeight.Black, 
                    color = tokens.colors.primary 
                ) 
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.small)) {
                    val sorts = listOf(
                        Triple("NAME", "Nome (A-Z)", Icons.Rounded.SortByAlpha),
                        Triple("RECENT", "Lançamento", Icons.Rounded.Schedule),
                        Triple("RATING", "Melhores Notas", Icons.Rounded.Star)
                    )
                    
                    sorts.forEach { (id, label, icon) ->
                        Surface(
                            onClick = { categoryViewModel.onSortOrderChanged(id); showSortMenu = false },
                            shape = tokens.shapes.medium,
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(tokens.spacing.medium),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(icon, null, tint = tokens.colors.primary, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(tokens.spacing.large))
                                Text(label, style = tokens.typography.body, color = tokens.colors.textPrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortMenu = false }) {
                    Text("FECHAR", color = tokens.colors.primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = tokens.colors.backgroundSecondary,
            shape = tokens.shapes.extraLarge
        )
    }
}
