package rsv.squitv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import rsv.squitv.core.ui.components.badges.AgeBadge
import rsv.squitv.core.ui.components.badges.GenreBadge
import rsv.squitv.core.ui.components.badges.RatingBadge
import rsv.squitv.core.ui.components.buttons.AppButton
import rsv.squitv.core.ui.components.buttons.AppIconButton
import rsv.squitv.core.ui.components.buttons.AppSecondaryButton
import rsv.squitv.core.ui.components.cards.CategoryCard
import rsv.squitv.core.ui.components.cards.ChannelCard
import rsv.squitv.core.ui.components.cards.PosterCard
import rsv.squitv.core.ui.components.common.AppSectionHeader
import rsv.squitv.core.ui.components.content.ActorAvatar
import rsv.squitv.core.ui.components.content.EpisodeCard
import rsv.squitv.core.ui.components.content.MetadataItem
import rsv.squitv.core.ui.components.inputs.AppTextField
import rsv.squitv.core.ui.components.inputs.SearchField
import rsv.squitv.core.ui.components.settings.SettingsActionItem
import rsv.squitv.core.ui.components.settings.SettingsGridCard
import rsv.squitv.core.ui.components.settings.SettingsStatCard
import rsv.squitv.core.ui.components.settings.SettingsToggle
import rsv.squitv.core.ui.theme.AppDesignSystem
import rsv.squitv.core.ui.theme.MeusCanaisTheme
import rsv.squitv.domain.model.ContentType
import rsv.squitv.domain.model.IptvItem

@Preview(name = "Design System • Tokens & Atoms", widthDp = 1200, heightDp = 1000)
@Composable
fun PreviewDesignSystemAtoms() {
    MeusCanaisTheme {
        val tokens = AppDesignSystem
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.colors.background)
                .padding(tokens.spacing.extraLarge)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "SQUI TV DESIGN SYSTEM", 
                style = tokens.typography.display, 
                color = tokens.colors.primary,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(tokens.spacing.huge))

            // COLORS
            SectionTitle("COLORS")
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.medium)) {
                ColorBox("Primary", tokens.colors.primary)
                ColorBox("Secondary", tokens.colors.secondary)
                ColorBox("Accent", tokens.colors.accent)
                ColorBox("Surface", tokens.colors.surface)
                ColorBox("Error", tokens.colors.error)
            }

            Spacer(modifier = Modifier.height(tokens.spacing.huge))

            // TYPOGRAPHY
            SectionTitle("TYPOGRAPHY")
            Text("Display Text", style = tokens.typography.display, color = tokens.colors.textPrimary)
            Text("Headline Text", style = tokens.typography.headline, color = tokens.colors.textPrimary)
            Text("Title Text", style = tokens.typography.title, color = tokens.colors.textPrimary)
            Text("Body Text", style = tokens.typography.body, color = tokens.colors.textPrimary)
            Text("Label Text", style = tokens.typography.label, color = tokens.colors.textPrimary)
            Text("Caption Text", style = tokens.typography.caption, color = tokens.colors.textPrimary)

            Spacer(modifier = Modifier.height(tokens.spacing.huge))

            // BUTTONS
            SectionTitle("BUTTONS")
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.large)) {
                AppButton("Principal", onClick = {})
                AppSecondaryButton("Secundário", onClick = {})
                AppIconButton(Icons.Rounded.Search, onClick = {})
                AppIconButton(Icons.Rounded.Favorite, onClick = {}, tint = tokens.colors.error)
            }

            Spacer(modifier = Modifier.height(tokens.spacing.huge))

            // BADGES & METADATA
            SectionTitle("BADGES & METADATA")
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.large), verticalAlignment = Alignment.CenterVertically) {
                AgeBadge("18")
                AgeBadge("L")
                RatingBadge("8.5")
                MetadataItem("2024")
                MetadataItem("2h 15min")
            }
            
            Spacer(modifier = Modifier.height(tokens.spacing.large))
            
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.large), verticalAlignment = Alignment.CenterVertically) {
                GenreBadge("Ação", onClick = {})
                GenreBadge("Comédia", onClick = {})
            }

            Spacer(modifier = Modifier.height(tokens.spacing.huge))

            // INPUTS
            SectionTitle("INPUTS")
            Column(modifier = Modifier.width(400.dp), verticalArrangement = Arrangement.spacedBy(tokens.spacing.large)) {
                AppTextField(value = "", onValueChange = {}, placeholder = "Usuário")
                SearchField(value = "Busca ativa", onValueChange = {})
            }
            
            Spacer(modifier = Modifier.height(tokens.spacing.giant))
        }
    }
}

@Preview(name = "Design System • Molecules", widthDp = 1200, heightDp = 1000)
@Composable
fun PreviewDesignSystemMolecules() {
    MeusCanaisTheme {
        val tokens = AppDesignSystem
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.colors.background)
                .padding(tokens.spacing.extraLarge)
                .verticalScroll(rememberScrollState())
        ) {
            SectionTitle("CARDS")
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.extraLarge)) {
                Box(modifier = Modifier.width(150.dp)) {
                    PosterCard(
                        item = IptvItem("1", "Oppenheimer", null, ContentType.MOVIE, isFavorite = true, rating = "8.6"),
                        progress = 0.6f,
                        onClick = {}
                    )
                }
                Box(modifier = Modifier.width(240.dp)) {
                    ChannelCard(
                        item = IptvItem("101", "ESPN Brasil", null, ContentType.LIVE),
                        epg = "Zapping agora...",
                        onClick = {}
                    )
                }
                CategoryCard(title = "Filmes", icon = Icons.Rounded.Movie, onClick = {})
            }

            Spacer(modifier = Modifier.height(tokens.spacing.huge))

            SectionTitle("DETAIL COMPONENTS")
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.extraLarge), verticalAlignment = Alignment.Top) {
                ActorAvatar(name = "Cillian Murphy", onClick = {})
                EpisodeCard(
                    title = "The Train Job",
                    episodeNum = 2,
                    image = null,
                    plot = "The crew is hired to pull a train heist, but things go sideways.",
                    progress = 0.4f,
                    isWatched = false,
                    onClick = {},
                    onDownload = {}
                )
            }

            Spacer(modifier = Modifier.height(tokens.spacing.huge))

            SectionTitle("SETTINGS COMPONENTS")
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(tokens.spacing.large)) {
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.large)) {
                    SettingsGridCard(label = "Conta", icon = Icons.Rounded.AccountCircle, onClick = {})
                    SettingsGridCard(label = "Sair", icon = Icons.AutoMirrored.Rounded.Logout, isDestructive = true, onClick = {})
                }
                SettingsActionItem(label = "Alterar Idioma", icon = Icons.Rounded.Language, trailingText = "Português", onClick = {})
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.large)) {
                    SettingsStatCard(label = "Conexões", value = "2/4", icon = Icons.Rounded.Dns, modifier = Modifier.weight(1f))
                    SettingsStatCard(label = "Expiração", value = "12/12/2024", icon = Icons.Rounded.Timer, isHighlight = true, modifier = Modifier.weight(1f))
                }
            }

            Spacer(modifier = Modifier.height(tokens.spacing.huge))

            SectionTitle("SECTION HEADERS")
            AppSectionHeader(title = "Lançamentos", onActionClick = {})
            Spacer(modifier = Modifier.height(tokens.spacing.medium))
            AppSectionHeader(title = "Canais de Esportes", actionText = "FILTRAR", onActionClick = {})
            
            Spacer(modifier = Modifier.height(tokens.spacing.giant))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    val tokens = AppDesignSystem
    Text(
        text = title, 
        style = tokens.typography.title, 
        color = tokens.colors.textSecondary,
        modifier = Modifier.padding(bottom = tokens.spacing.large)
    )
}

@Composable
private fun ColorBox(label: String, color: Color) {
    val tokens = AppDesignSystem
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(60.dp).background(color, tokens.shapes.medium))
        Spacer(modifier = Modifier.height(tokens.spacing.tiny))
        Text(label, style = tokens.typography.caption, color = tokens.colors.textSecondary)
    }
}

@Preview(name = "Ícones • celular, tablet, TV e notificações", widthDp = 860, heightDp = 340)
@Composable
fun PreviewAppIcons() {
    MeusCanaisTheme {
        Column(
            Modifier.fillMaxSize().background(AppDesignSystem.colors.background).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(
                    rsv.squitv.R.drawable.ic_launcher_legacy to "Legado",
                    rsv.squitv.R.drawable.ic_launcher_round_legacy to "Redondo",
                    rsv.squitv.R.drawable.ic_launcher_monochrome to "Monocromático",
                    rsv.squitv.R.drawable.ic_notification to "Notificação"
                ).forEach { (resource, label) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(resource),
                            contentDescription = label,
                            modifier = Modifier.size(if (label == "Notificação") 24.dp else 72.dp)
                        )
                        Text(label, color = AppDesignSystem.colors.textPrimary)
                    }
                }
            }
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(rsv.squitv.R.drawable.banner_tv),
                contentDescription = "Banner Android TV / Fire TV",
                modifier = Modifier.size(280.dp, 157.5.dp)
            )
        }
    }
}
