package rsv.squitv.desktop

import kotlinx.serialization.json.*

internal data class DesktopBackupContents(val preferences: DesktopPreferences, val available: Set<DesktopBackupGroup>)

/** Android's explicit field selection/availability pattern, adapted to Windows preferences. */
internal object DesktopBackupCodec {
    const val MAX_BYTES = 64 * 1024
    private const val FORMAT = "SquiTV.Windows.Settings"
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }
    private val fields = mapOf(
        DesktopBackupGroup.PLAYBACK to setOf("autoPlayEpisodes", "volume", "muted", "keepAudioOnMinimize",
            "preferredAudioLanguage", "preferredSubtitleLanguage"),
        DesktopBackupGroup.INTERFACE to setOf("interfaceScale", "fullscreen", "hiddenSections", "language",
            "alwaysOnTop", "ignoredUpdateVersion", "browseMemory"),
        DesktopBackupGroup.CATEGORIES to setOf("preferredChannelQuality", "groupLiveChannels", "hiddenCategoryIds",
            "hiddenLiveCategoriesByAccount", "liveCategoryOrderByAccount", "catalogCategoryOrderByScope",
            "hiddenCatalogCategoriesByScope", "catalogModesByScope"),
        DesktopBackupGroup.FAVORITES to setOf("favoriteLiveChannelsByAccount", "favoriteCatalogItemsByScope"),
        DesktopBackupGroup.DISPLAY_NAMES to setOf("displayNamesByAccount")
    )

    private fun keys(groups: Set<DesktopBackupGroup>) = setOf("schema") + groups.flatMap { fields.getValue(it) }

    fun encode(preferences: DesktopPreferences, selected: Set<DesktopBackupGroup>): String {
        require(selected.isNotEmpty())
        validateDesktopPreferences(preferences)
        val all = json.encodeToJsonElement(DesktopPreferences.serializer(), preferences).jsonObject
        val included = keys(selected)
        val document = buildJsonObject {
            put("format", FORMAT)
            put("schemaVersion", 1)
            put("includedGroups", JsonArray(DesktopBackupGroup.entries.filter { it in selected }.map { JsonPrimitive(it.name) }))
            put("preferences", JsonObject(all.filterKeys { it in included }))
        }
        return json.encodeToString(JsonObject.serializer(), document).also {
            require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        }
    }

    fun decode(text: String): DesktopBackupContents {
        require(text.toByteArray(Charsets.UTF_8).size in 1..MAX_BYTES)
        val root = json.parseToJsonElement(text.removePrefix("\uFEFF")) as? JsonObject ?: error("Invalid Windows backup")
        val groups: Set<DesktopBackupGroup>
        val values: JsonObject
        if ("format" in root || "schemaVersion" in root) {
            require(root["format"] == JsonPrimitive(FORMAT) && root["schemaVersion"] == JsonPrimitive(1))
            val declared = root["includedGroups"] as? JsonArray ?: error("Missing backup groups")
            require(declared.size in 1..DesktopBackupGroup.entries.size)
            val names = declared.map { value ->
                val name = value as? JsonPrimitive ?: error("Invalid backup group")
                require(name.isString)
                DesktopBackupGroup.valueOf(name.content)
            }
            require(names.distinct().size == names.size)
            groups = names.toSet()
            values = root["preferences"] as? JsonObject ?: error("Missing backup preferences")
            // A declared group must contain all its fields: defaults must never replace omitted data.
            require(values.keys == keys(groups))
        } else {
            // Existing Windows full exports included these defaults and no selection metadata.
            require("includedGroups" !in root)
            require(setOf("autoPlayEpisodes", "interfaceScale", "volume", "fullscreen", "groupLiveChannels", "preferredChannelQuality")
                .all(root::containsKey))
            groups = DesktopBackupGroup.entries.toSet()
            values = root
        }
        require(values["schema"] == JsonPrimitive(1))
        val preferences = json.decodeFromJsonElement(DesktopPreferences.serializer(), values).also(::validateDesktopPreferences)
        return DesktopBackupContents(preferences, groups)
    }
}
