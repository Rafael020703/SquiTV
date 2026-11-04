package rsv.squitv.desktop

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import rsv.squitv.desktop.data.WindowsCredentialStore
import rsv.squitv.shared.xtream.ServerCredentials

class WindowsCredentialStoreTest {
    private fun isolated(action: (Path) -> Unit) {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val directory = Files.createTempDirectory("squitv-accounts-review-")
        try { action(directory) }
        finally {
            listOf("account.dpapi", "accounts.dpapi", "debug-login.dpapi", "login.paused").forEach {
                Files.deleteIfExists(directory.resolve(it))
            }
            Files.deleteIfExists(directory)
        }
    }
    @Test fun multipleAccountsUpdateAndRemoveWithoutDeletingOtherAccounts() = isolated { directory ->
        val store = WindowsCredentialStore(directory)
        val first = ServerCredentials("https://one.invalid", "fixture-one", "fixture-one")
        val second = ServerCredentials("https://two.invalid", "fixture-two", "fixture-two")
        val updated = first.copy(password = "fixture-updated")
        store.save(first); store.save(second); store.save(updated)
        assertEquals(setOf(updated, second), store.savedAccounts().toSet())
        assertEquals(updated, store.load())
        store.setAutomaticLogin(false)
        assertFalse(store.allowsAutomaticLogin())
        store.save(second)
        assertTrue(store.allowsAutomaticLogin())
        store.removeAccount(liveCategoryScope(updated)!!)
        assertEquals(listOf(second), store.savedAccounts())
        assertEquals(second, store.load())
    }
    @Test fun debugDefaultsNeverBecomeRememberedAccountsOrReleaseDefaultsImplicitly() = isolated { directory ->
        val fixture = ServerCredentials("https://defaults.invalid", "fixture-default", "fixture-default")
        WindowsCredentialStore(directory).save(fixture)
        Files.move(directory.resolve("account.dpapi"), directory.resolve("debug-login.dpapi"))
        Files.delete(directory.resolve("accounts.dpapi"))
        val debug = WindowsCredentialStore(directory, useDebugDefaults = true)
        assertEquals(fixture, debug.load())
        assertTrue(debug.savedAccounts().isEmpty())
        assertFalse(debug.allowsAutomaticLogin())
        debug.clear()
        assertEquals(fixture, debug.load())
        assertNull(WindowsCredentialStore(directory).load())
    }
    @Test fun unreadableVaultDoesNotOverwriteRememberedCredentials() = isolated { directory ->
        val store = WindowsCredentialStore(directory)
        val fixture = ServerCredentials("https://fixture.invalid", "fixture", "fixture")
        store.save(fixture)
        val before = Files.readAllBytes(directory.resolve("account.dpapi"))
        val corrupt = byteArrayOf(1, 2, 3)
        Files.write(directory.resolve("accounts.dpapi"), corrupt)
        try { store.save(fixture.copy(password = "fixture-new")); fail("Unreadable vault must be preserved") }
        catch (_: Exception) { }
        assertArrayEquals(before, Files.readAllBytes(directory.resolve("account.dpapi")))
        assertArrayEquals(corrupt, Files.readAllBytes(directory.resolve("accounts.dpapi")))
        assertEquals(fixture, store.load())
    }
}
