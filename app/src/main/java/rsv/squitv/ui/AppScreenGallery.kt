@file:OptIn(UnstableApi::class)
package rsv.squitv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import rsv.squitv.core.ui.components.common.adaptiveFocus
import rsv.squitv.core.ui.responsive.ResponsiveLayout
import rsv.squitv.core.ui.theme.AppDesignSystem
import rsv.squitv.core.ui.theme.MeusCanaisTheme
import rsv.squitv.ui.content.LiveChannelsScreen
import rsv.squitv.ui.settings.SettingsScreen
import rsv.squitv.ui.viewmodel.CategoryViewModel
import rsv.squitv.ui.viewmodel.MainViewModel
import rsv.squitv.ui.viewmodel.library.LibraryViewModel
import rsv.squitv.ui.viewmodel.settings.SettingsViewModel

/**
 * APP GALLERY SCREEN — LABORATÓRIO VISUAL (RUNTIME & RESPONSIVE PREVIEWS)
 * 
 * Permite inspecionar em tempo de execução e em modo paisagem estrito
 * as telas principais (SettingsScreen e LiveChannelsScreen) em múltiplos
 * formatos de tela (Compacto 640x360dp, Intermediário 800x450dp, Amplo 1280x720dp e Full HD).
 */
@Composable
fun AppGalleryScreen(
    onBack: (() -> Unit)? = null,
    mainViewModel: MainViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel()
) {
    var selectedScreenIndex by remember { mutableIntStateOf(0) } // 0 = Settings, 1 = Live TV
    var selectedPresetIndex by remember { mutableIntStateOf(0) }

    val screens = remember { listOf("CONFIGURAÇÕES" to 0, "TV AO VIVO (CATÁLOGO)" to 1) }

    val landscapePresets = remember {
        listOf(
            "TELA CHEIA" to null,
            "COMPACTO (640×360)" to Pair(640.dp, 360.dp),
            "INTERMEDIÁRIO (800×450)" to Pair(800.dp, 450.dp),
            "AMPLO (1280×720)" to Pair(1280.dp, 720.dp),
            "FULL HD (1920×1080)" to Pair(1440.dp, 810.dp) // Scaled preview for Full HD aspect ratio
        )
    }

    MeusCanaisTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppDesignSystem.colors.background)
        ) {
            // --- HEADER DO LABORATÓRIO VISUAL ---
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0D1322).copy(alpha = 0.95f),
                shadowElevation = 12.dp,
                border = BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        colors = listOf(
                            AppDesignSystem.colors.primary.copy(alpha = 0.4f),
                            AppDesignSystem.colors.border.copy(alpha = 0.15f),
                            AppDesignSystem.colors.secondary.copy(alpha = 0.3f)
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        AppDesignSystem.colors.primary.copy(alpha = 0.15f),
                                        CircleShape
                                    )
                                    .border(
                                        1.dp,
                                        AppDesignSystem.colors.primary.copy(alpha = 0.4f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Science,
                                    contentDescription = null,
                                    tint = AppDesignSystem.colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "SQUITV • LABORATÓRIO VISUAL RESPONSIVO (PAISAGEM)",
                                    style = AppDesignSystem.typography.headline.copy(fontSize = 15.sp),
                                    fontWeight = FontWeight.Black,
                                    color = AppDesignSystem.colors.textPrimary,
                                    letterSpacing = 1.2.sp
                                )
                                Text(
                                    text = "VALIDAÇÃO DE RESPONSIVIDADE E GRADE EM MÚLTIPLOS FORMATOS HORIZONTAIS",
                                    style = AppDesignSystem.typography.caption.copy(fontSize = 9.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = AppDesignSystem.colors.primary,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        // TELA SELEÇÃO CHIPS
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            screens.forEach { (label, idx) ->
                                val isSelected = selectedScreenIndex == idx
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedScreenIndex = idx },
                                    modifier = Modifier.adaptiveFocus(shape = AppDesignSystem.shapes.small),
                                    label = {
                                        Text(
                                            text = label,
                                            style = AppDesignSystem.typography.caption.copy(fontSize = 10.sp),
                                            fontWeight = FontWeight.Black
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AppDesignSystem.colors.primary,
                                        selectedLabelColor = AppDesignSystem.colors.background,
                                        containerColor = AppDesignSystem.colors.surfaceElevated.copy(alpha = 0.6f),
                                        labelColor = AppDesignSystem.colors.textSecondary
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // CHIPS DE PRESET DE DISPOSITIVO EM PAISAGEM
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        landscapePresets.forEachIndexed { index, (label, _) ->
                            val isSelected = selectedPresetIndex == index
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedPresetIndex = index },
                                modifier = Modifier.adaptiveFocus(shape = AppDesignSystem.shapes.small),
                                label = {
                                    Text(
                                        text = label,
                                        style = AppDesignSystem.typography.caption.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Black
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppDesignSystem.colors.secondary,
                                    selectedLabelColor = AppDesignSystem.colors.background,
                                    containerColor = AppDesignSystem.colors.surfaceElevated.copy(alpha = 0.6f),
                                    labelColor = AppDesignSystem.colors.textSecondary
                                )
                            )
                        }
                    }
                }
            }

            // --- ÁREA DE CONTEÚDO RESPONSIVO ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (selectedPresetIndex == 0) 0.dp else 16.dp),
                contentAlignment = Alignment.Center
            ) {
                val currentPreset = landscapePresets[selectedPresetIndex].second

                val contentComposable: @Composable () -> Unit = {
                    if (selectedScreenIndex == 0) {
                        SettingsScreen(
                            mainViewModel = mainViewModel,
                            settingsViewModel = settingsViewModel,
                            libraryViewModel = libraryViewModel,
                            onBack = { onBack?.invoke() },
                            onNavigateToAccount = {},
                            onNavigateToDnsTester = {},
                            onNavigateToUpdates = {}
                        )
                    } else {
                        LiveChannelsScreen(
                            viewModel = mainViewModel,
                            categoryViewModel = categoryViewModel,
                            settingsViewModel = settingsViewModel,
                            onBack = { onBack?.invoke() },
                            onPlay = { _, _, _, _, _, _ -> },
                            onCategoryClick = { _, _ -> },
                            onNavigateToSearch = {}
                        )
                    }
                }

                if (currentPreset == null) {
                    ResponsiveLayout {
                        contentComposable()
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .width(currentPreset.first)
                            .height(currentPreset.second)
                            .shadow(
                                elevation = 24.dp,
                                shape = AppDesignSystem.shapes.extraLarge,
                                ambientColor = AppDesignSystem.colors.primary.copy(alpha = 0.3f),
                                spotColor = AppDesignSystem.colors.primary
                            )
                            .border(
                                BorderStroke(
                                    1.5.dp,
                                    Brush.linearGradient(
                                        colors = listOf(
                                            AppDesignSystem.colors.primary.copy(alpha = 0.6f),
                                            AppDesignSystem.colors.border.copy(alpha = 0.25f),
                                            AppDesignSystem.colors.secondary.copy(alpha = 0.4f)
                                        )
                                    )
                                ),
                                AppDesignSystem.shapes.extraLarge
                            ),
                        shape = AppDesignSystem.shapes.extraLarge,
                        color = Color(0xFF0D1322).copy(alpha = 0.9f)
                    ) {
                        ResponsiveLayout {
                            contentComposable()
                        }
                    }
                }
            }
        }
    }
}
