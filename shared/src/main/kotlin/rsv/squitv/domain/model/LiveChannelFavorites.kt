package rsv.squitv.domain.model

/** Android live favorites: save the selected address; removing a group removes its saved versions. */
object LiveChannelFavorites {
    fun familyIds(streamId: Int, versionIds: Iterable<Int>): Set<Int> = versionIds.toSet() + streamId

    fun isFavorite(savedIds: Set<Int>, streamId: Int, versionIds: Iterable<Int> = emptyList()): Boolean =
        streamId in savedIds || versionIds.any { it in savedIds }

    fun toggle(savedIds: Set<Int>, streamId: Int, versionIds: Iterable<Int> = emptyList()): Set<Int> {
        val family = familyIds(streamId, versionIds)
        return if (family.any { it in savedIds }) savedIds - family else savedIds + streamId
    }
}
