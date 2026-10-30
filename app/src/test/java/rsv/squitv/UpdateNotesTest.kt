package rsv.squitv

import org.junit.Assert.*
import org.junit.Test
import rsv.squitv.ui.updates.releaseHighlights

class UpdateNotesTest {
    @Test fun blank() { assertTrue(releaseHighlights(" \n ").isEmpty()) }
    @Test fun readable() {
        assertEquals(listOf("Correção do player", "Detalhes no site"), releaseHighlights("## Novidades\n- **Correção** do `player`\n- Detalhes no [site](https://example.com)"))
    }
    @Test fun technicalSections() {
        val body = "## Novidades\n- Aviso compacto\n## Download\n- Arquivo APK\n### Checksum\n- HASH\n## Como usar\nAbra as configurações"
        assertEquals(listOf("Aviso compacto", "Abra as configurações"), releaseHighlights(body))
    }
    @Test fun codeAndLimit() {
        assertTrue(releaseHighlights("```kotlin\nval secret = 1\n```").isEmpty())
        assertEquals(60, releaseHighlights((1..100).joinToString("\n") { "- Item $it" }).size)
    }
}
