package rsv.squitv.domain.model

enum class ChannelQuality(val rank: Int) {
    AUTO(0), SD(1), HD(2), FHD(3), UHD(4);
    companion object {
        fun fromPreference(value: String) = entries.firstOrNull { it.name == value } ?: AUTO
    }
}
