package rsv.squitv.ui.content

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.media3.common.util.UnstableApi
import rsv.squitv.core.ui.theme.MeusCanaisTheme
import rsv.squitv.data.model.XtreamCategory
import rsv.squitv.domain.model.ContentType
import rsv.squitv.domain.model.DashboardRow
import rsv.squitv.domain.model.IptvItem
import rsv.squitv.ui.dashboard.PortalBackground
import rsv.squitv.core.ui.components.navigation.AppHeader
import rsv.squitv.core.ui.components.navigation.AppSidebar
import rsv.squitv.ui.content.components.ContentGrid
import androidx.compose.ui.focus.FocusRequester

@Preview(name = "Minimal Compose Preview • Landscape", widthDp = 800, heightDp = 450, backgroundColor = 0xFF0D1322, showBackground = true)
@Composable
fun MinimalComposePreview() {
    MeusCanaisTheme {
        PortalBackground {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "SquiTV • Compose Preview Working", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@UnstableApi
@Preview(name = "Live TV Catalog • Wide Landscape (1280x720)", widthDp = 1280, heightDp = 720, showBackground = true, backgroundColor = 0xFF0D1322)
@Composable
fun LiveChannelsCatalogWidePreview() {
    val sampleCategories = listOf(
        XtreamCategory("RECENTS", "Adicionados recentemente"),
        XtreamCategory("FAVORITES", "Favoritos"),
        XtreamCategory("1", "Esportes HD"),
        XtreamCategory("2", "Canais Abertos"),
        XtreamCategory("3", "Filmes & Séries")
    )

    val sampleItems = listOf(
        IptvItem("1", "Globo HD", "https://images.unsplash.com/photo-1594909122845-11baa439b7bf?w=400", ContentType.LIVE, epgId = "Jornal Nacional", qualities = mapOf("FHD" to 1)),
        IptvItem("2", "ESPN Brasil", "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=400", ContentType.LIVE, epgId = "Futebol Ao Vivo", qualities = mapOf("4K" to 2)),
        IptvItem("3", "Telecine Premium", "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=400", ContentType.LIVE, epgId = "Filme Inédito", qualities = mapOf("HD" to 3)),
        IptvItem("4", "Discovery Channel", "https://images.unsplash.com/photo-1507679799987-c73779587ccf?w=400", ContentType.LIVE, epgId = "Documentário", qualities = mapOf("FHD" to 4)),
        IptvItem("5", "Cartoon Network", "https://images.unsplash.com/photo-1560169897-5fc9b57795b7?w=400", ContentType.LIVE, epgId = "Desenhos Animados", qualities = mapOf("HD" to 5))
    )

    val sampleRows = listOf(
        DashboardRow("Canais Ao Vivo", sampleItems, catId = "1")
    )

    MeusCanaisTheme {
        PortalBackground {
            Column(modifier = Modifier.fillMaxSize()) {
                AppHeader(
                    title = "TV AO VIVO",
                    subtitle = "Esportes HD",
                    onBack = {},
                    actions = {}
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    AppSidebar(
                        items = sampleCategories,
                        selectedItemPredicate = { it.categoryId == "1" },
                        itemLabel = { it.categoryName ?: "" },
                        onItemClick = {},
                        onItemFocus = {},
                        focusRequester = remember { FocusRequester() },
                        nextFocusRequester = remember { FocusRequester() }
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        ContentGrid(
                            items = sampleRows.flatMap { it.items },
                            gridFocusRequester = remember { FocusRequester() },
                            sidebarFocusRequester = remember { FocusRequester() },
                            onItemClick = {}
                        )
                    }
                }
            }
        }
    }
}

@UnstableApi
@Preview(name = "Live TV Catalog • Compact Landscape (640x360)", widthDp = 640, heightDp = 360, showBackground = true, backgroundColor = 0xFF0D1322)
@Composable
fun LiveChannelsCatalogCompactPreview() {
    LiveChannelsCatalogWidePreview()
}
