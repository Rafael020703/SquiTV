package rsv.squitv.desktop

/** Windows adapters for Android's selected preferences, scoped categories and additive favorites. */
internal enum class DesktopBackupGroup(val label: String) {
    PLAYBACK("Reprodução"),
    INTERFACE("Interface e navegação"),
    CATEGORIES("Categorias e organização"),
    FAVORITES("Favoritos"),
    DISPLAY_NAMES("Nomes de exibição")
}

internal fun mergeDesktopBackup(current: DesktopPreferences, incoming: DesktopPreferences,
    selected: Set<DesktopBackupGroup>, available: Set<DesktopBackupGroup> = DesktopBackupGroup.entries.toSet()): DesktopPreferences {
    require(selected.isNotEmpty() && available.containsAll(selected))
    validateDesktopPreferences(incoming)
    var result = current
    if (DesktopBackupGroup.PLAYBACK in selected) result = result.copy(
        autoPlayEpisodes = incoming.autoPlayEpisodes, volume = incoming.volume, muted = incoming.muted,
        keepAudioOnMinimize = incoming.keepAudioOnMinimize,
        preferredAudioLanguage = incoming.preferredAudioLanguage, preferredSubtitleLanguage = incoming.preferredSubtitleLanguage)
    if (DesktopBackupGroup.INTERFACE in selected) result = result.copy(
        interfaceScale = incoming.interfaceScale, fullscreen = incoming.fullscreen,
        hiddenSections = incoming.hiddenSections, language = incoming.language, alwaysOnTop = incoming.alwaysOnTop,
        ignoredUpdateVersion = incoming.ignoredUpdateVersion,
        browseMemory = current.browseMemory.copy(positions = current.browseMemory.positions + incoming.browseMemory.positions))
    if (DesktopBackupGroup.CATEGORIES in selected) result = result.copy(
        preferredChannelQuality = incoming.preferredChannelQuality, groupLiveChannels = incoming.groupLiveChannels,
        // Legacy unscoped IDs stay unscoped; never infer which account owns them.
        hiddenCategoryIds = current.hiddenCategoryIds + incoming.hiddenCategoryIds,
        hiddenLiveCategoriesByAccount = current.hiddenLiveCategoriesByAccount + incoming.hiddenLiveCategoriesByAccount,
        liveCategoryOrderByAccount = current.liveCategoryOrderByAccount + incoming.liveCategoryOrderByAccount,
        catalogCategoryOrderByScope = current.catalogCategoryOrderByScope + incoming.catalogCategoryOrderByScope,
        hiddenCatalogCategoriesByScope = current.hiddenCatalogCategoriesByScope + incoming.hiddenCatalogCategoriesByScope,
        catalogModesByScope = current.catalogModesByScope + incoming.catalogModesByScope)
    if (DesktopBackupGroup.FAVORITES in selected) result = result.copy(
        favoriteLiveChannelsByAccount = mergeFavoriteIds(current.favoriteLiveChannelsByAccount, incoming.favoriteLiveChannelsByAccount),
        favoriteCatalogItemsByScope = mergeFavoriteIds(current.favoriteCatalogItemsByScope, incoming.favoriteCatalogItemsByScope))
    if (DesktopBackupGroup.DISPLAY_NAMES in selected) result = result.copy(
        displayNamesByAccount = current.displayNamesByAccount + incoming.displayNamesByAccount)
    // Validate the combined limits too; the store rejects oversize output before atomic replacement.
    return result.also(::validateDesktopPreferences)
}

private fun mergeFavoriteIds(current: Map<String, Set<Int>>, incoming: Map<String, Set<Int>>) =
    current + incoming.mapValues { (scope, ids) -> current[scope].orEmpty() + ids }
