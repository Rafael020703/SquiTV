package rsv.squitv.shared.xtream

/** Same provider endpoints used by Android and Windows. Not operating-system DNS resolvers. */
object ProviderServers {
    const val PRIMARY = "http://dnssquitv.rafael020703.shop"
    const val PLAYBACK_START_TIMEOUT_MS = 5_000L

    val addresses: List<String> = listOf(
        PRIMARY, "http://ded30.com", "http://ded34.com", "http://ded15.com",
        "http://now1.org", "http://ppmm1.cc", "https://u123.life")

    private val normalizedAddresses = addresses.map(ServerAddress::normalize)

    fun loginCandidates(custom: String, selected: String?, options: List<String> = addresses): List<String> {
        if (custom.isNotBlank()) return listOf(ServerAddress.normalize(custom))
        return (listOfNotNull(selected) + options).map(ServerAddress::normalize).distinct()
    }

    fun livePlaybackServers(configuredAddress: String): List<String> {
        val configured = ServerAddress.normalize(configuredAddress)
        return if (configured in normalizedAddresses) normalizedAddresses else listOf(configured)
    }
}
