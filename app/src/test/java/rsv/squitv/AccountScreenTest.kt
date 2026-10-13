package rsv.squitv

import org.junit.Assert.*
import org.junit.Test
import rsv.squitv.ui.account.formatExpirationDate

class AccountScreenTest {

    @Test
    fun formatExpirationDate_nullOrBlank_returnsNaoInformado() {
        assertEquals("Não informado", formatExpirationDate(null))
        assertEquals("Não informado", formatExpirationDate(""))
        assertEquals("Não informado", formatExpirationDate("   "))
        assertEquals("Não informado", formatExpirationDate("null"))
        assertEquals("Não informado", formatExpirationDate("0"))
    }

    @Test
    fun formatExpirationDate_validUnixTimestamp_returnsFormattedDate() {
        // Timestamp 1750000000 -> 15/06/2025 (or UTC/Local equivalent)
        val formatted = formatExpirationDate("1750000000")
        assertNotEquals("Não informado", formatted)
        assertTrue("Formatted date should contain '/' or '2025'", formatted.contains("2025") || formatted.contains("/"))
    }

    @Test
    fun formatExpirationDate_outOfRangeTimestamp_returnsNaoInformado() {
        assertEquals("Não informado", formatExpirationDate("9999999999999"))
        assertEquals("Não informado", formatExpirationDate("-12345"))
    }

    @Test
    fun formatExpirationDate_invalidString_returnsNaoInformado() {
        assertEquals("Não informado", formatExpirationDate("invalid_timestamp"))
        assertEquals("Não informado", formatExpirationDate("abc1234567"))
    }
}
