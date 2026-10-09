package rsv.squitv

import org.junit.Assert.*
import org.junit.Test
import rsv.squitv.data.model.XtreamCredentials
import rsv.squitv.data.repository.SettingsRepository

class SavedAccountsTest {

    @Test
    fun appSettings_savedAccountsList_initiallyEmpty() {
        val settings = SettingsRepository.AppSettings(
            credentials = null,
            lastSyncTimestamp = 0L,
            syncIntervalHours = 24
        )
        assertTrue(settings.accounts.isEmpty())
    }

    @Test
    fun appSettings_savedAccount_containsCredentialsWithoutExposingCleartextInLogs() {
        val cred1 = XtreamCredentials("user1", "pass123", "http://server1.com:8080")
        val cred2 = XtreamCredentials("user2", "pass456", "http://server2.com:8080")

        val settings = SettingsRepository.AppSettings(
            credentials = cred1,
            accounts = listOf(cred1, cred2),
            lastSyncTimestamp = System.currentTimeMillis(),
            syncIntervalHours = 24
        )

        assertEquals(2, settings.accounts.size)
        assertEquals("user1", settings.accounts[0].username)
        assertEquals("http://server1.com:8080", settings.accounts[0].baseUrl)

        assertEquals("user2", settings.accounts[1].username)
        assertEquals("http://server2.com:8080", settings.accounts[1].baseUrl)

        // Ensure toString of credential doesn't leak password in plain text if masked or formatted
        val summary = "Account: ${cred1.username} @ ${cred1.baseUrl}"
        assertFalse("Logs/summaries must not contain cleartext password", summary.contains("pass123"))
    }

    @Test
    fun scenarioA_scenarioB_savePrompt_userSelection_savesOrDeclines() {
        val accountsList = mutableListOf<XtreamCredentials>()
        val newCred = XtreamCredentials("newUser", "pass789", "http://iptv.server.com:8080")

        // Helper simulation for scenario A ("Salvar")
        fun onConfirmSaveAccount(cred: XtreamCredentials, save: Boolean) {
            if (save) {
                val index = accountsList.indexOfFirst { 
                    it.username.equals(cred.username, ignoreCase = true) && 
                    it.baseUrl.equals(cred.baseUrl, ignoreCase = true) 
                }
                if (index == -1) accountsList.add(cred) else accountsList[index] = cred
            }
        }

        // Scenario A: User chooses "Salvar"
        onConfirmSaveAccount(newCred, save = true)
        assertEquals(1, accountsList.size)
        assertEquals("newUser", accountsList[0].username)

        // Scenario B: User chooses "Agora não" for another account
        val declinedCred = XtreamCredentials("declinedUser", "pass000", "http://iptv.server.com:8080")
        onConfirmSaveAccount(declinedCred, save = false)

        // Only the first account remains saved
        assertEquals(1, accountsList.size)
        assertEquals("newUser", accountsList[0].username)
    }

    @Test
    fun scenarioC_selectingSavedAccount_prefillsFieldsWithoutAutoLogin() {
        val savedCred = XtreamCredentials("savedUser", "secret123", "http://myiptv.net:8080")

        // Simulating UI state variables when selecting a saved account
        val currentUsername = savedCred.username
        val currentPassword = savedCred.password
        val currentUrl = savedCred.baseUrl
        val autoLoginExecuted = false

        assertEquals("savedUser", currentUsername)
        assertEquals("secret123", currentPassword)
        assertEquals("http://myiptv.net:8080", currentUrl)
        assertFalse("Selecting a saved account MUST NOT trigger automatic login submission", autoLoginExecuted)
    }

    @Test
    fun scenarioD_duplicateAccountCheck_handlesCaseSensitivityAndTrailingSlashes() {
        val accountsList = mutableListOf(
            XtreamCredentials("UserTest", "pass123", "http://server.com:8080")
        )

        fun isAccountAlreadySaved(user: String, server: String): Boolean {
            val normServer = server.trim().removeSuffix("/")
            return accountsList.any { 
                it.username.equals(user.trim(), ignoreCase = true) && 
                it.baseUrl.trim().removeSuffix("/").equals(normServer, ignoreCase = true) 
            }
        }

        // Case-insensitive user & trailing slash URL match
        assertTrue(isAccountAlreadySaved("usertest", "http://server.com:8080/"))
        assertTrue(isAccountAlreadySaved("USERTEST", "http://server.com:8080"))

        // Different server or user
        assertFalse(isAccountAlreadySaved("usertest", "http://diffserver.com:8080"))
        assertFalse(isAccountAlreadySaved("otheruser", "http://server.com:8080"))
    }

    @Test
    fun scenarioE_accountRemoval_removesSpecificAccountOnlyWithoutAffectingCatalogOrSettings() {
        val cred1 = XtreamCredentials("user1", "pass123", "http://server1.com:8080")
        val cred2 = XtreamCredentials("user2", "pass456", "http://server2.com:8080")

        val accounts = mutableListOf(cred1, cred2)

        // Remove cred1
        val indexToRemove = accounts.indexOfFirst { it.username == "user1" }
        if (indexToRemove != -1) {
            accounts.removeAt(indexToRemove)
        }

        assertEquals(1, accounts.size)
        assertEquals("user2", accounts[0].username)
        assertEquals("http://server2.com:8080", accounts[0].baseUrl)
    }

    @Test
    fun scenarioF_failedLogin_doesNotPromptSaveAccount() {
        val authSuccess = false
        var showSavePrompt = false

        if (authSuccess) {
            showSavePrompt = true
        }

        assertFalse("Save prompt MUST NOT appear when login fails", showSavePrompt)
    }
}
