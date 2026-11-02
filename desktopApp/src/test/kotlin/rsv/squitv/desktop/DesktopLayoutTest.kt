@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package rsv.squitv.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.assertTrue
import rsv.squitv.desktop.data.*
import rsv.squitv.shared.xtream.*

class DesktopLayoutTest {
    @Test fun rendersLoginAndConnectedLayoutsAtMinimumAndDefaultSize() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = object : CredentialStore {
            override fun load(): ServerCredentials? = null
            override fun save(credentials: ServerCredentials) {}
            override fun clear() {}
        }
        DesktopController(XtreamClient(), store, scope).use { controller ->
            val directory = Path.of("build", "preview")
            Files.createDirectories(directory)
            val connected = DesktopState(restoring = false,
                credentials = ServerCredentials("https://servidor.example/", "Conta demonstração", "fixture"),
                account = ServerAccount("Conta demonstração", "Active", 2000000000, 1, 2,
                    "America/Sao_Paulo", null, listOf("ts")),
                categories = listOf(LiveCategory("1", "Notícias"), LiveCategory("2", "Esportes")),
                selectedCategory = LiveCategory("1", "Notícias"),
                channels = listOf(LiveChannel(1, "Canal de notícias HD", "1", null, null),
                    LiveChannel(2, "Notícias ao vivo", "1", null, null)))
            for ((name, state) in listOf("login" to DesktopState(restoring = false), "canais" to connected)) {
                for ((width, height) in listOf(1120 to 760, 860 to 580)) {
                    val scene = ImageComposeScene(width, height, Density(1f)) {
                        MaterialTheme(colorScheme = palette) {
                            Surface(Modifier.fillMaxSize(), color = palette.background) { DesktopScreen(state, controller) }
                        }
                    }
                    try {
                        scene.render().close()
                        val image = scene.render()
                        try {
                            val data = image.encodeToData()!!
                            try {
                                assertTrue(data.bytes.size > 1000)
                                Files.write(directory.resolve("$name-$width.png"), data.bytes)
                            } finally { data.close() }
                        } finally { image.close() }
                    } finally { scene.close() }
                }
            }
        }
        scope.cancel()
    }
}
