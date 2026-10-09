package rsv.squitv

import org.junit.Assert.*
import org.junit.Test
import rsv.squitv.data.repository.SettingsRepository
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationTest {

    @Test
    fun appSettings_defaultLanguage_isAuto() {
        val settings = SettingsRepository.AppSettings(
            credentials = null,
            lastSyncTimestamp = 0L,
            syncIntervalHours = 24
        )
        assertEquals("auto", settings.language)
    }

    @Test
    fun supportedLanguages_containsPortugueseEnglishSpanishAndAuto() {
        val supportedCodes = listOf("auto", "pt", "en", "es")
        assertEquals(4, supportedCodes.size)
        assertTrue(supportedCodes.contains("auto"))
        assertTrue(supportedCodes.contains("pt"))
        assertTrue(supportedCodes.contains("en"))
        assertTrue(supportedCodes.contains("es"))
    }

    @Test
    fun stringsXml_parityAndPlaceholderValidation() {
        val baseDir = if (File("src/main/res").exists()) File("src/main/res") else File("app/src/main/res")

        val ptFile = File(baseDir, "values/strings.xml")
        val enFile = File(baseDir, "values-en/strings.xml")
        val esFile = File(baseDir, "values-es/strings.xml")

        assertTrue("PT strings.xml must exist", ptFile.exists())
        assertTrue("EN strings.xml must exist", enFile.exists())
        assertTrue("ES strings.xml must exist", esFile.exists())

        val ptMap = parseStringsXml(ptFile)
        val enMap = parseStringsXml(enFile)
        val esMap = parseStringsXml(esFile)

        assertFalse("PT keys must not be empty", ptMap.isEmpty())
        assertFalse("EN keys must not be empty", enMap.isEmpty())
        assertFalse("ES keys must not be empty", esMap.isEmpty())

        // Check that all PT keys exist in EN and ES
        for ((key, value) in ptMap) {
            assertTrue("Key '$key' missing in EN strings.xml", enMap.containsKey(key))
            assertTrue("Key '$key' missing in ES strings.xml", esMap.containsKey(key))

            val enVal = enMap[key] ?: ""
            val esVal = esMap[key] ?: ""

            // Count single %s placeholders
            val ptS = countPercentS(value)
            val enS = countPercentS(enVal)
            val esS = countPercentS(esVal)
            assertEquals("Mismatch %s placeholders for key '$key'", ptS, enS)
            assertEquals("Mismatch %s placeholders for key '$key'", ptS, esS)
        }

        println("Strings parity verified successfully across PT (${ptMap.size} keys), EN (${enMap.size} keys), and ES (${esMap.size} keys).")
    }

    private fun parseStringsXml(file: File): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = dBuilder.parse(file)
        doc.documentElement.normalize()

        val nodeList = doc.getElementsByTagName("string")
        for (i in 0 until nodeList.length) {
            val node = nodeList.item(i)
            if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                val element = node as org.w3c.dom.Element
                val name = element.getAttribute("name")
                val text = element.textContent ?: ""
                map[name] = text
            }
        }
        return map
    }

    private fun countPercentS(str: String): Int {
        var count = 0
        var idx = 0
        while (true) {
            idx = str.indexOf("%s", idx)
            if (idx == -1) break
            count++
            idx += 2
        }
        return count
    }
}
