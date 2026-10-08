@file:OptIn(UnstableApi::class)
package rsv.squitv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import rsv.squitv.core.ui.components.buttons.AppIconButton
import rsv.squitv.core.ui.components.common.adaptiveFocus
import rsv.squitv.core.ui.responsive.ResponsiveLayout
import rsv.squitv.core.ui.theme.AppDesignSystem
import rsv.squitv.core.ui.theme.MeusCanaisTheme
import rsv.squitv.ui.settings.SettingsScreen
import rsv.squitv.ui.viewmodel.MainViewModel
import rsv.squitv.ui.viewmodel.library.LibraryViewModel
import rsv.squitv.ui.viewmodel.settings.SettingsViewModel

/**
 * APP GALLERY SCREEN — LABORATÓRIO VISUAL DA SETTINGS SCREEN (RUNTIME)
 * 
 * Apresenta a interface visual real da SettingsScreen dentro do container
 * de laboratório, permitindo testar dinamicamente em tempo de execução
 * a grade de configurações, foco D-Pad, ordenação alfabética e simulações
 * de formato de tela sem alterar a SettingsScreen nativa.
 */
@Composable
fun AppGalleryScreen(
    onBack: (() -> Unit)? = null,
    mainViewModel: MainViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel()
) {
    var selectedPresetIndex by remember { mutableIntStateOf(0) }

    val presets = remember {
        listOf(
            "TELA CHEIA" to null,
            "TV 1080p" to Pair(1280.dp, 720.dp),
            "PHONE PORTRAIT" to Pair(360.dp, 800.dp),
            "PHONE LANDSCAPE" to Pair(800.dp, 360.dp),
            "ULTRA SMALL" to Pair(320.dp, 568.dp),
            "TABLET PORTRAIT" to Pair(768.dp, 1024.dp)
        )
    }

    MeusCanaisTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppDesignSystem.colors.background)
        ) {
            // --- HEADER DO LABORATÓRIO EM RUNTIME ---
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0D1322).copy(alpha = 0.9f),
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
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
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
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "LABORATÓRIO VISUAL • SETTINGS SCREEN",
                                    style = AppDesignSystem.typography.headline.copy(fontSize = 16.sp),
                                    fontWeight = FontWeight.Black,
                                    color = AppDesignSystem.colors.textPrimary,
                                    letterSpacing = 1.5.sp
                                )
                                Text(
                                    text = "EXPLORE A GRADE DE CONFIGURAÇÕES E TESTE OS DISPOSITIVOS EM RUNTIME",
                                    style = AppDesignSystem.typography.caption.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = AppDesignSystem.colors.primary,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        Surface(
                            color = AppDesignSystem.colors.primary.copy(alpha = 0.2f),
                            shape = AppDesignSystem.shapes.small,
                            border = BorderStroke(1.dp, AppDesignSystem.colors.primary.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "RUNTIME MODE",
                                style = AppDesignSystem.typography.caption.copy(fontSize = 10.sp),
                                color = AppDesignSystem.colors.primary,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // CHIPS DE PRESET DE DISPOSITIVO EM RUNTIME
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        presets.forEachIndexed { index, (label, _) ->
                            val isSelected = selectedPresetIndex == index
                            var isChipFocused by remember { mutableStateOf(false) }

                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedPresetIndex = index },
                                modifier = Modifier.adaptiveFocus(
                                    shape = AppDesignSystem.shapes.small,
                                    glowColor = AppDesignSystem.colors.primary,
                                    focusedScale = 1.05f,
                                    onFocus = { isChipFocused = it }
                                ),
                                label = {
                                    Text(
                                        text = label,
                                        style = AppDesignSystem.typography.caption.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppDesignSystem.colors.primary,
                                    selectedLabelColor = AppDesignSystem.colors.background,
                                    containerColor = AppDesignSystem.colors.surfaceElevated.copy(alpha = 0.6f),
                                    labelColor = AppDesignSystem.colors.textSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = AppDesignSystem.colors.border.copy(alpha = 0.2f),
                                    selectedBorderColor = AppDesignSystem.colors.primary
                                )
                            )
                        }
                    }
                }
            }

            // --- ÁREA DE CONTEÚDO REAL DA SETTINGS SCREEN ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (selectedPresetIndex == 0) 0.dp else 20.dp),
                contentAlignment = Alignment.Center
            ) {
                val currentPreset = presets[selectedPresetIndex].second

                if (currentPreset == null) {
                    // MODO TELA NATIVA COMPLETA COM SETTINGS SCREEN REAL
                    ResponsiveLayout {
                        SettingsScreen(
                            mainViewModel = mainViewModel,
                            settingsViewModel = settingsViewModel,
                            libraryViewModel = libraryViewModel,
                            onBack = { onBack?.invoke() },
                            onNavigateToAccount = {},
                            onNavigateToDnsTester = {},
                            onNavigateToUpdates = {}
                        )
                    }
                } else {
                    // MODO FRAME SIMULADO EM RUNTIME COM SETTINGS SCREEN REAL
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
                        color = Color(0xFF0D1322).copy(alpha = 0.85f)
                    ) {
                        ResponsiveLayout {
                            SettingsScreen(
                                mainViewModel = mainViewModel,
                                settingsViewModel = settingsViewModel,
                                libraryViewModel = libraryViewModel,
                                onBack = { onBack?.invoke() },
                                onNavigateToAccount = {},
                                onNavigateToDnsTester = {},
                                onNavigateToUpdates = {}
                            )
                        }
                    }
                }
            }
        }
    }
}
