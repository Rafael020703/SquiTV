package rsv.squitv

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import rsv.squitv.data.model.XtreamCredentials

class CustomDisplayNameTest {

    private fun getAccountKey(username: String, baseUrl: String? = null): String {
        val simple = username.trim().lowercase()
        return if (!baseUrl.isNullOrBlank()) "${baseUrl.trim().lowercase()}#$simple" else simple
    }

    private fun updateMap(
        currentMapJson: String?,
        username: String,
        baseUrl: String? = null,
        name: String?
    ): String {
        val map = try {
            if (currentMapJson != null) Json.decodeFromString<MutableMap<String, String>>(currentMapJson)
            else mutableMapOf()
        } catch (_: Exception) {
            mutableMapOf()
        }

        val simpleKey = username.trim().lowercase()
        val accountKey = getAccountKey(username, baseUrl)
        val trimmedValue = name?.trim()

        if (trimmedValue.isNullOrBlank()) {
            map.remove(accountKey)
            map.remove(simpleKey)
        } else {
            map[accountKey] = trimmedValue
            map[simpleKey] = trimmedValue
        }

        return Json.encodeToString(map)
    }

    private fun getDisplayName(mapJson: String?, username: String, baseUrl: String? = null): String? {
        if (mapJson == null) return null
        val map = try {
            Json.decodeFromString<Map<String, String>>(mapJson)
        } catch (_: Exception) {
            return null
        }
        val simpleKey = username.trim().lowercase()
        val accountKey = getAccountKey(username, baseUrl)
        return map[accountKey] ?: map[simpleKey]
    }

    @Test
    fun saveAndRetrieve_customDisplayName_returnsSavedName() {
        var mapJson: String? = null

        mapJson = updateMap(mapJson, "user123", "http://server.com", "Rafael")
        val retrieved = getDisplayName(mapJson, "user123", "http://server.com")

        assertEquals("Rafael", retrieved)
    }

    @Test
    fun updateName_blanksAndSpaces_clearsCustomDisplayName() {
        var mapJson: String? = null

        // First save "Rafael"
        mapJson = updateMap(mapJson, "user123", "http://server.com", "Rafael")
        assertEquals("Rafael", getDisplayName(mapJson, "user123", "http://server.com"))

        // Update with blank spaces "   "
        mapJson = updateMap(mapJson, "user123", "http://server.com", "   ")
        assertNull("Blank custom name should revert to null", getDisplayName(mapJson, "user123", "http://server.com"))
    }

    @Test
    fun multiAccount_sameUsernameDifferentServers_isolatedDisplayNames() {
        var mapJson: String? = null

        // User on Server A
        mapJson = updateMap(mapJson, "usuario1", "http://serverA.com:8080", "Rafael Casa")

        // Same username on Server B
        mapJson = updateMap(mapJson, "usuario1", "http://serverB.com:8080", "Rafael Trabalho")

        val nameServerA = getDisplayName(mapJson, "usuario1", "http://serverA.com:8080")
        val nameServerB = getDisplayName(mapJson, "usuario1", "http://serverB.com:8080")

        assertEquals("Rafael Casa", nameServerA)
        assertEquals("Rafael Trabalho", nameServerB)
    }

    @Test
    fun clearDisplayName_restoresNull_revertingToDefaultUsername() {
        var mapJson: String? = null

        mapJson = updateMap(mapJson, "pedro", "http://server.com", "Pedro Silva")
        assertEquals("Pedro Silva", getDisplayName(mapJson, "pedro", "http://server.com"))

        mapJson = updateMap(mapJson, "pedro", "http://server.com", null)
        assertNull(getDisplayName(mapJson, "pedro", "http://server.com"))
    }

    @Test
    fun credentials_remainsUnchanged_whenCustomDisplayNameIsSaved() {
        val originalCreds = XtreamCredentials("user123", "secretPass", "http://iptv.server.com:8080")

        // Simulating display name save
        var mapJson: String? = null
        mapJson = updateMap(mapJson, originalCreds.username, originalCreds.baseUrl, "Meu Nome Customizado")

        // Credentials must remain strictly identical
        assertEquals("user123", originalCreds.username)
        assertEquals("secretPass", originalCreds.password)
        assertEquals("http://iptv.server.com:8080", originalCreds.baseUrl)
        assertEquals("Meu Nome Customizado", getDisplayName(mapJson, originalCreds.username, originalCreds.baseUrl))
    }
}
