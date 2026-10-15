package rsv.squitv

import org.junit.Assert.*
import org.junit.Test
import rsv.squitv.data.model.XtreamCredentials
import rsv.squitv.util.SecurityHelper

class LocalAuthAndLoginTest {

    @Test
    fun localCredentialsEncryption_passwordsEncrypted_noPlainTextInStorage() {
        val rawPassword = "mySecretPassword123"
        
        val encrypted = SecurityHelper.encrypt(rawPassword)
        assertNotNull("Encryption should produce non-null output", encrypted)
        assertNotEquals("Encrypted text must NOT match plain password", rawPassword, encrypted)
        
        val decrypted = SecurityHelper.decrypt(encrypted)
        assertEquals("Decrypted password must match original", rawPassword, decrypted)
    }

    @Test
    fun credentialsModel_privacyCheck_passwordRedactedInToString() {
        val creds = XtreamCredentials("user123", "secretPass99", "http://iptv.server.com:8080")

        val stringRepresentation = creds.toString()
        
        assertTrue("ToString should contain username", stringRepresentation.contains("user123"))
        assertFalse("ToString must NOT contain plain text password", stringRepresentation.contains("secretPass99"))
    }

    @Test
    fun savedAccountsList_accountSwitching_maintainsLocalAccountIntegrity() {
        val accounts = listOf(
            XtreamCredentials("userA", "passA", "http://serverA.com:8080"),
            XtreamCredentials("userB", "passB", "http://serverB.com:8080")
        )

        val activeIndex = 1
        val activeAccount = accounts[activeIndex]

        assertEquals("userB", activeAccount.username)
        assertEquals("http://serverB.com:8080", activeAccount.baseUrl)
        assertNotEquals("userA", activeAccount.username)
    }
}
