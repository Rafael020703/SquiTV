package rsv.squitv.shared.xtream

/** Same provider endpoints used by Android and Windows. Not operating-system DNS resolvers. */
object ProviderServers {
    val addresses: List<String> = listOf(
        "http://ded30.com", "http://ded34.com", "http://ded15.com",
        "http://now1.org", "http://ppmm1.cc", "https://u123.life")

    fun loginCandidates(custom: String, selected: String?, options: List<String> = addresses): List<String> {
        if (custom.isNotBlank()) return listOf(ServerAddress.normalize(custom))
        return (listOfNotNull(selected) + options).map(ServerAddress::normalize).distinct()
    }
}
