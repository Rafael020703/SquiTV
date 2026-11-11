package rsv.squitv.shared.xtream

import org.junit.Assert.*
import org.junit.Test

class XtreamCatalogTest {
    @Test fun normalizesHostPortAndApiEndpoint() {
        assertEquals("http://example.com:8080/", ServerAddress.normalize("example.com:8080"))
        assertEquals("https://example.com/base/", ServerAddress.normalize("https://EXAMPLE.com/base/player_api.php"))
        assertEquals("https://example.com/a%20b/", ServerAddress.normalize("https://example.com/a%20b"))
    }

    @Test fun rejectsUnsafeServerAddresses() {
        for (input in listOf("", "ftp://example.com", "http://user:secret@example.com", "https://example.com/?password=x",
            "https://example.com/#fragment", "http://example.com:0", "https://example.com:99999")) {
            assertThrows(IllegalArgumentException::class.java) { ServerAddress.normalize(input) }
        }
    }

    @Test fun automaticLoginAndLivePlaybackPreferThePrimaryServer() {
        val order = ProviderServers.addresses.map(ServerAddress::normalize)
        assertEquals(ServerAddress.normalize(ProviderServers.PRIMARY), order.first())
        assertEquals(order, ProviderServers.loginCandidates("", null))
        assertEquals(order, ProviderServers.livePlaybackServers("https://u123.life/"))
        assertEquals(order.size, order.toSet().size)
    }

    @Test fun customProviderDoesNotReceiveFallbackServers() {
        val custom = "https://other.example:8443/subdirectory"
        val expected = listOf("https://other.example:8443/subdirectory/")
        assertEquals(expected, ProviderServers.loginCandidates(custom, ProviderServers.PRIMARY))
        assertEquals(expected, ProviderServers.livePlaybackServers(custom))
    }

    @Test fun parsesMixedScalarAccountAndServerData() {
        val account = XtreamCatalogParser.account("""
            {"user_info":{"auth":"1","status":"Active","username":"demo","exp_date":"2000000000",
            "active_cons":1,"max_connections":"2","allowed_output_formats":["ts","m3u8"]},
            "server_info":{"timezone":"America/Sao_Paulo","time_now":"2026-11-01 10:00:00"}}
        """.trimIndent(), "fallback", 1000)
        assertEquals("demo", account.username)
        assertEquals(1, account.activeConnections)
        assertEquals(2, account.maxConnections)
        assertEquals("America/Sao_Paulo", account.timezone)
        assertEquals(listOf("ts", "m3u8"), account.outputFormats)
    }

    @Test fun rejectsInvalidInactiveAndExpiredAccounts() {
        for (body in listOf("""{"user_info":{"auth":0}}""",
            """{"user_info":{"auth":1,"status":"Banned"}}""",
            """{"user_info":{"auth":1,"status":"Active","exp_date":999}}""")) {
            assertThrows(ServerAccountException::class.java) { XtreamCatalogParser.account(body, "demo", 1000) }
        }
    }

    @Test fun acceptsUnlimitedAccountAndBoundsDates() {
        assertNull(XtreamCatalogParser.account("""{"user_info":{"auth":1,"exp_date":"0"}}""", "demo", 1000).expiresAt)
        assertNull(XtreamCatalogParser.account("""{"user_info":{"auth":1,"exp_date":"9223372036854775807"}}""", "demo", 1000).expiresAt)
    }

    @Test fun categoriesHaveStableIdsAndAlphabeticalOrder() {
        val categories = XtreamCatalogParser.categories("""
            [{"category_id":2,"category_name":"Zebra"},{"category_id":"1","category_name":"Animal"},
            {"category_id":2,"category_name":"Duplicada"},{"category_name":"Sem id"}]
        """.trimIndent())
        assertEquals(listOf("1", "2"), categories.map { it.id })
    }

    @Test fun filtersInvalidDuplicateChannelsAndKeepsProviderOrder() {
        val channels = XtreamCatalogParser.channels("""
            [{"stream_id":"23","name":"B","category_id":7},{"stream_id":20,"name":"A"},
            {"stream_id":23,"name":"Duplicado"},{"stream_id":0},{"stream_id":"invalid"}]
        """.trimIndent())
        assertEquals(listOf(23, 20), channels.map { it.id })
        assertEquals("7", channels.first().categoryId)
    }

    @Test fun rejectsNonListCatalogAndMissingAccount() {
        assertThrows(IllegalArgumentException::class.java) { XtreamCatalogParser.categories("{}") }
        assertThrows(IllegalArgumentException::class.java) { XtreamCatalogParser.channels("{}") }
        assertThrows(IllegalArgumentException::class.java) { XtreamCatalogParser.account("{}", "demo", 1000) }
    }

    @Test fun credentialsToStringDoesNotExposeSecrets() {
        val printed = ServerCredentials("https://private.invalid", "private-user", "secret-value").toString()
        assertFalse(printed.contains("secret-value"))
        assertFalse(printed.contains("private-user"))
        assertFalse(printed.contains("private.invalid"))
    }
}
