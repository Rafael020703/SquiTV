package rsv.squitv.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import rsv.squitv.util.rememberWindowInfo
import rsv.squitv.R
import rsv.squitv.core.ui.components.buttons.AppButton
import rsv.squitv.core.ui.components.common.adaptiveFocus
import rsv.squitv.core.ui.components.navigation.AppHeader
import rsv.squitv.core.ui.components.settings.SettingsToggle
import rsv.squitv.core.ui.components.states.EmptyState
import rsv.squitv.core.ui.components.states.LoadingState
import rsv.squitv.core.ui.theme.AppDesignSystem
import rsv.squitv.data.local.entities.IptvCategoryEntity
import rsv.squitv.data.model.XtreamCredentials
import rsv.squitv.domain.model.ContentType
import rsv.squitv.ui.dashboard.PortalBackground
import rsv.squitv.ui.viewmodel.MainViewModel

import androidx.annotation.OptIn

enum class CustomizationTab(val title: String, val icon: ImageVector) {
    SECTIONS("ABAS / SEÇÕES", Icons.Rounded.Dashboard),
    LIVE_TV("TV AO VIVO", Icons.Rounded.Monitor),
    MOVIES("FILMES", Icons.Rounded.Movie),
    SERIES("SÉRIES", Icons.Rounded.VideoLibrary)
}

@OptIn(UnstableApi::class)
@Composable
fun ContentCustomizationScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit
) {
    val tokens = AppDesignSystem
    var activeTab by remember { mutableStateOf(CustomizationTab.SECTIONS) }
    val appSettings by mainViewModel.appSettings.collectAsStateWithLifecycle()
    val credentials = mainViewModel.credentials
    val hiddenSections by mainViewModel.hiddenSections.collectAsStateWithLifecycle()

    var liveCategories by remember { mutableStateOf<List<IptvCategoryEntity>>(emptyList()) }
    var vodCategories by remember { mutableStateOf<List<IptvCategoryEntity>>(emptyList()) }
    var seriesCategories by remember { mutableStateOf<List<IptvCategoryEntity>>(emptyList()) }
    var isLoadingCategories by remember { mutableStateOf(false) }

    LaunchedEffect(credentials, activeTab) {
        if (credentials != null && activeTab != CustomizationTab.SECTIONS) {
            isLoadingCategories = true
            try {
                when (activeTab) {
                    CustomizationTab.LIVE_TV -> {
                        liveCategories = mainViewModel.catalogRepository.getLiveCategories(credentials)
                    }
                    CustomizationTab.MOVIES -> {
                        vodCategories = mainViewModel.catalogRepository.getVodCategories(credentials)
                    }
                    CustomizationTab.SERIES -> {
                        seriesCategories = mainViewModel.catalogRepository.getSeriesCategories(credentials)
                    }
                    else -> {}
                }
            } catch (_: Exception) {}
            finally { isLoadingCategories = false }
        }
    }

    PortalBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            AppHeader(
                title = "PERSONALIZAR CONTEÚDO",
                subtitle = "Organize suas abas e categorias do seu jeito",
                onBack = onBack,
                showBackButton = true
            )

            // TOP TAB BAR
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(CustomizationTab.entries) { _, tab ->
                    val isSelected = activeTab == tab
                    var isFocused by remember { mutableStateOf(false) }

                    Surface(
                        modifier = Modifier
                            .adaptiveFocus(
                                shape = tokens.shapes.medium,
                                focusedScale = 1.04f,
                                onFocus = { isFocused = it }
                            )
                            .clickable { activeTab = tab },
                        shape = tokens.shapes.medium,
                        color = if (isFocused) tokens.colors.primary.copy(alpha = 0.25f)
                                else if (isSelected) tokens.colors.primary.copy(alpha = 0.15f)
                                else tokens.colors.surfaceVariant.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                tint = if (isFocused || isSelected) tokens.colors.primary else tokens.colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = tab.title,
                                style = tokens.typography.label.copy(fontSize = 11.sp),
                                fontWeight = if (isFocused || isSelected) FontWeight.Black else FontWeight.Bold,
                                color = if (isFocused || isSelected) tokens.colors.primary else tokens.colors.textPrimary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // TAB CONTENT
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                when (activeTab) {
                    CustomizationTab.SECTIONS -> {
                        DashboardSectionsCustomizer(
                            hiddenSections = hiddenSections,
                            onToggleSection = mainViewModel::toggleSectionVisibility,
                            onResetSections = mainViewModel::restoreDefaultSections
                        )
                    }
                    CustomizationTab.LIVE_TV -> {
                        CategoryCustomizerTab(
                            contentType = "live",
                            rawCategories = liveCategories,
                            appSettings = appSettings,
                            credentials = credentials,
                            isLoading = isLoadingCategories,
                            mainViewModel = mainViewModel
                        )
                    }
                    CustomizationTab.MOVIES -> {
                        CategoryCustomizerTab(
                            contentType = "movie",
                            rawCategories = vodCategories,
                            appSettings = appSettings,
                            credentials = credentials,
                            isLoading = isLoadingCategories,
                            mainViewModel = mainViewModel
                        )
                    }
                    CustomizationTab.SERIES -> {
                        CategoryCustomizerTab(
                            contentType = "series",
                            rawCategories = seriesCategories,
                            appSettings = appSettings,
                            credentials = credentials,
                            isLoading = isLoadingCategories,
                            mainViewModel = mainViewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardSectionsCustomizer(
    hiddenSections: Set<String>,
    onToggleSection: (String) -> Unit,
    onResetSections: () -> Unit
) {
    val tokens = AppDesignSystem
    val sections = listOf(
        "LIVE_TV" to "TV Ao Vivo",
        "MOVIES" to "Filmes",
        "SERIES" to "Séries",
        "EPG" to "Guia TV (EPG)",
        "MULTI_VIEW" to "Multi-View",
        "DOWNLOADS" to "Downloads",
        "FAVORITES" to "Favoritos"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 800.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            Surface(
                color = tokens.colors.surface.copy(alpha = 0.1f),
                shape = tokens.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(tokens.spacing.large),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.medium)
                ) {
                    Text(
                        text = "SEÇÕES DA TELA INICIAL (DASHBOARD)",
                        style = tokens.typography.label.copy(fontSize = 12.sp),
                        color = tokens.colors.primary,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Oculte ou ative os cards principais exibidos na Dashboard.",
                        style = tokens.typography.body.copy(fontSize = 12.sp),
                        color = tokens.colors.textSecondary
                    )

                    sections.forEach { (key, title) ->
                        val isVisible = key !in hiddenSections
                        SettingsToggle(
                            label = title,
                            checked = isVisible,
                            onCheckedChange = { _ -> onToggleSection(key) }
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                AppButton(
                    text = "RESTAURAR PADRÃO",
                    icon = Icons.Rounded.RestartAlt,
                    onClick = onResetSections,
                    useGradient = false,
                    modifier = Modifier.height(42.dp)
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun CategoryCustomizerTab(
    contentType: String,
    rawCategories: List<IptvCategoryEntity>,
    appSettings: rsv.squitv.data.repository.SettingsRepository.AppSettings,
    credentials: XtreamCredentials?,
    isLoading: Boolean,
    mainViewModel: MainViewModel
) {
    val tokens = AppDesignSystem

    if (isLoading) {
        LoadingState(message = "Carregando categorias...")
        return
    }

    if (rawCategories.isEmpty()) {
        EmptyState(
            title = "NENHUMA CATEGORIA ENCONTRADA",
            description = "Certifique-se de que a sincronização do catálogo foi concluída."
        )
        return
    }

    val simpleUser = credentials?.username?.trim()?.lowercase() ?: ""
    val cleanUrl = credentials?.baseUrl?.trim()?.lowercase()?.removeSuffix("/") ?: ""
    val accountKey = if (cleanUrl.isNotBlank()) "$cleanUrl#$simpleUser" else simpleUser
    val normType = if (contentType == "movie") "vod" else contentType.lowercase()
    val typeKey = "${accountKey}_${normType}"

    val customOrder = appSettings.categoryOrderMap[typeKey]
    val hiddenSet = appSettings.hiddenCategoriesMap[typeKey] ?: emptySet()

    val sortedCategories = remember(rawCategories, customOrder) {
        if (!customOrder.isNullOrEmpty()) {
            val orderMap = customOrder.withIndex().associate { it.value to it.index }
            rawCategories.sortedWith(compareBy { orderMap[it.id] ?: Int.MAX_VALUE })
        } else {
            rawCategories
        }
    }

    val totalCount = rawCategories.size
    val visibleCount = rawCategories.count { it.id !in hiddenSet }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }

    fun getUpFocusRequester(catId: String): FocusRequester =
        focusRequesters.getOrPut("${catId}_up") { FocusRequester() }

    fun getDownFocusRequester(catId: String): FocusRequester =
        focusRequesters.getOrPut("${catId}_down") { FocusRequester() }

    fun getToggleFocusRequester(catId: String): FocusRequester =
        focusRequesters.getOrPut("${catId}_toggle") { FocusRequester() }

    val latestCategories by rememberUpdatedState(sortedCategories)
    val windowInfo = rememberWindowInfo()
    val compactActions = windowInfo.screenWidth < 600.dp
    val useFocus = windowInfo.isTv || LocalInputModeManager.current.inputMode == InputMode.Keyboard
    var isMoving by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }

    fun moveAndFollow(catId: String, index: Int, direction: Int) {
        if (isMoving) return
        isMoving = true
        coroutineScope.launch {
            try {
                val expected = if (direction == 0) 0 else (index + direction).coerceIn(0, sortedCategories.lastIndex)
                val job = when (direction) {
                    0 -> mainViewModel.moveCategoryToTop(contentType, catId, sortedCategories)
                    -1 -> mainViewModel.moveCategoryUp(contentType, catId, sortedCategories)
                    else -> mainViewModel.moveCategoryDown(contentType, catId, sortedCategories)
                }
                job.join()
                withTimeoutOrNull(1500) {
                    snapshotFlow { latestCategories.indexOfFirst { it.id == catId } }.first { it == expected }
                }
                val destination = latestCategories.indexOfFirst { it.id == catId }.coerceAtLeast(0)
                listState.scrollToItem(destination)
                withFrameNanos { }
                if (useFocus) runCatching { getToggleFocusRequester(catId).requestFocus() }
            } finally {
                isMoving = false
            }
        }
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text(stringResource(R.string.customization_reset_title)) },
            text = { Text(stringResource(R.string.customization_reset_description)) },
            confirmButton = {
                TextButton(onClick = {
                    mainViewModel.resetCategoryCustomization(contentType)
                    showResetConfirmation = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // TOP SUMMARY BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EXIBINDO $visibleCount DE $totalCount CATEGORIAS",
                style = tokens.typography.label.copy(fontSize = 11.sp),
                color = tokens.colors.primary,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { mainViewModel.showAllCategories(contentType) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("MOSTRAR TODAS", fontSize = 11.sp, color = tokens.colors.primary, fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = { showResetConfirmation = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("RESTAURAR PADRÃO", fontSize = 11.sp, color = tokens.colors.error, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // CATEGORIES LIST WITH D-PAD REORDER CONTROLS & AUTO-CENTERING
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            itemsIndexed(sortedCategories, key = { _, cat -> cat.id }) { index, cat ->
                val isHidden = cat.id in hiddenSet

                Surface(
                    color = if (isHidden) tokens.colors.surface.copy(alpha = 0.05f) else tokens.colors.surface.copy(alpha = 0.15f),
                    shape = tokens.shapes.medium,
                    border = BorderStroke(1.dp, if (isHidden) Color.White.copy(alpha = 0.05f) else tokens.colors.primary.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val categoryLabel: @Composable (Modifier) -> Unit = { labelModifier ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = labelModifier
                        ) {
                            Text(
                                text = "#${index + 1}",
                                style = tokens.typography.caption.copy(fontSize = 10.sp),
                                color = tokens.colors.textSecondary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(32.dp)
                            )
                            Text(
                                text = cat.name.uppercase(),
                                style = tokens.typography.body.copy(fontSize = 12.sp),
                                fontWeight = FontWeight.Bold,
                                color = if (isHidden) tokens.colors.textSecondary.copy(alpha = 0.5f) else tokens.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    val categoryActions: @Composable () -> Unit = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { moveAndFollow(cat.id, index, 0) },
                                enabled = index > 0 && !isMoving,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Rounded.VerticalAlignTop,
                                    contentDescription = stringResource(R.string.category_move_top),
                                    tint = if (index > 0) tokens.colors.primary else Color.Gray.copy(alpha = 0.3f),
                                    modifier = Modifier.size(20.dp))
                            }

                            // Move Up Button
                            IconButton(
                                onClick = { moveAndFollow(cat.id, index, -1) },
                                enabled = index > 0 && !isMoving,
                                modifier = Modifier.size(48.dp).focusRequester(getUpFocusRequester(cat.id))
                            ) {
                                Icon(Icons.Rounded.ArrowUpward,
                                    contentDescription = stringResource(R.string.category_move_up),
                                    tint = if (index > 0) tokens.colors.primary else Color.Gray.copy(alpha = 0.3f),
                                    modifier = Modifier.size(18.dp))
                            }

                            // Move Down Button
                            IconButton(
                                onClick = { moveAndFollow(cat.id, index, 1) },
                                enabled = index < sortedCategories.size - 1 && !isMoving,
                                modifier = Modifier.size(48.dp).focusRequester(getDownFocusRequester(cat.id))
                            ) {
                                Icon(Icons.Rounded.ArrowDownward,
                                    contentDescription = stringResource(R.string.category_move_down),
                                    tint = if (index < sortedCategories.size - 1) tokens.colors.primary else Color.Gray.copy(alpha = 0.3f),
                                    modifier = Modifier.size(18.dp))
                            }

                            // Hide/Show Toggle Button
                            IconButton(
                                onClick = {
                                    mainViewModel.toggleCategoryVisibility(contentType, cat.id)
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .focusRequester(getToggleFocusRequester(cat.id))
                            ) {
                                Icon(
                                    imageVector = if (isHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (isHidden) "Mostrar categoria" else "Ocultar categoria",
                                    tint = if (isHidden) tokens.colors.error else tokens.colors.success,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    if (compactActions) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                            categoryLabel(Modifier.fillMaxWidth())
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { categoryActions() }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            categoryLabel(Modifier.weight(1f))
                            categoryActions()
                        }
                    }
                }
            }
        }
    }
}
