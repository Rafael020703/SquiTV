package rsv.squitv

import org.junit.Assert.*
import org.junit.Test
import rsv.squitv.domain.model.*
import rsv.squitv.core.data.repository.PlaybackSourceRepository

class LiveChannelGroupingTest {
    private fun item(id: Int, name: String, category: String = "a", epg: String? = "channel") =
        IptvItem(id.toString(), name, null, ContentType.LIVE, categoryId = category, epgId = epg)
    private fun grouped(vararg items: IptvItem, quality: ChannelQuality = ChannelQuality.AUTO) =
        LiveChannelGrouping.group(items.toList(), "account/profile", quality)

    @Test fun resolutionsAreRecognizedAsCompleteSuffixes() {
        mapOf("SD" to ChannelQuality.SD, "HD" to ChannelQuality.HD, "720p" to ChannelQuality.HD,
            "FHD" to ChannelQuality.FHD, "1080p" to ChannelQuality.FHD, "UHD" to ChannelQuality.UHD,
            "4K" to ChannelQuality.UHD, "2160p" to ChannelQuality.UHD).forEach { (tag, expected) ->
            assertEquals(expected, LiveChannelGrouping.parse("Canal [$tag]").resolution)
            assertEquals("Canal", LiveChannelGrouping.parse("Canal ($tag)").name)
        }
    }
    @Test fun codecsStaySeparateAndEquivalentVersionsAreNeverOverwritten() {
        val group = grouped(item(3, "Canal FHD HEVC"), item(1, "Canal 1080p H.264"),
            item(2, "Canal FHD H265"), item(4, "Canal FHD")).single()
        assertEquals(listOf(1, 2, 3, 4), group.versions.map { it.streamId })
        assertEquals(setOf(ChannelQuality.FHD), group.versions.map { it.resolution }.toSet())
        assertEquals(listOf("H.264", "H.265", "H.265", null), group.versions.map { it.codec })
    }
    @Test fun legitimateNamePartsAreNeverRemoved() {
        listOf("HDTV", "SHD News", "UHDream", "HD Brasil", "Canal 4", "Canal HD SP",
            "Canal LOW", "Canal +1", "Canal 2 EN", "Canal Internacional").forEach {
            assertEquals(it, LiveChannelGrouping.parse(it).name)
        }
    }
    @Test fun regionalLanguageNumberAndEditionDifferencesArePreserved() {
        val names = listOf("Canal SP HD", "Canal RJ FHD", "Canal EN HD", "Canal ES HD",
            "Canal 1 HD", "Canal 2 HD", "Canal +1 HD", "Canal Premium HD")
        assertEquals(names.size, LiveChannelGrouping.group(names.mapIndexed { i, n -> item(i + 1, n) }, "scope").size)
    }
    @Test fun categoryAndEpgIdentityAreBoundaries() {
        assertEquals(3, grouped(item(1, "Canal HD", "a", "one"),
            item(2, "Canal SD", "b", "one"), item(3, "Canal FHD", "a", "two")).size)
        assertEquals(1, grouped(item(1, "Canal HD", epg = null), item(2, "Canal SD", epg = "known")).size)
    }
    @Test fun unsupportedLiveIdsStayIndividualWithoutNumericConversion() {
        val items = listOf(item(1, "Canal HD").copy(id = "legacy-a"),
            item(2, "Canal SD").copy(id = "legacy-b"))
        assertEquals(items, LiveChannelGrouping.group(items, "scope"))
    }
    @Test fun qualityDelimitersAndBracketedPrefixesAreCompleteMarkers() {
        listOf("Canal-HD", "Canal / 720p", "[FHD] Canal", "4K | Canal", "Canal {HD}").forEach {
            assertEquals("Canal", LiveChannelGrouping.parse(it).name)
        }
        assertEquals("HD Brasil", LiveChannelGrouping.parse("HD Brasil").name)
        assertEquals("Canal 2 SP", LiveChannelGrouping.parse("[FHD] Canal 2 SP HEVC").name)
    }
    @Test fun providerEpgQualityAliasesAndMissingIdsDoNotSplitTheSameChannel() {
        val items = listOf(item(1, "Canal HD", epg = "canal.hd"),
            item(2, "Canal FHD", epg = "canal.fhd"), item(3, "Canal SD", epg = null))
        assertEquals(listOf(1, 2, 3), LiveChannelGrouping.group(items, "scope").single().versions.map { it.streamId })
        val ambiguous = items + item(4, "Canal 4K", epg = "other-region")
        assertEquals(3, LiveChannelGrouping.group(ambiguous, "scope").size)
    }
    @Test fun repeatedOffOnCyclesRetainAllVersionsAndRemoteStepsSkipQualitySiblings() {
        val raw = listOf(item(1, "Canal SD"), item(2, "Canal HD"), item(3, "Canal FHD"),
            item(4, "Outro HD"), item(5, "Outro 4K"))
        var visible = LiveChannelGrouping.visible(raw, "scope", true, ChannelQuality.AUTO)
        val key = visible.first().stableKey
        repeat(4) {
            visible = LiveChannelGrouping.visible(visible, "scope", false, ChannelQuality.AUTO)
            assertEquals(5, visible.size)
            visible = LiveChannelGrouping.visible(visible, "scope", true, ChannelQuality.AUTO)
            assertEquals(2, visible.size)
            assertEquals(key, visible.first().stableKey)
            assertEquals(3, visible.first().versions.size)
        }
        for (id in listOf("1", "2", "3")) {
            assertEquals("Outro", LiveChannelGrouping.adjacent(visible, id, 1)!!.name)
            assertEquals("Outro", LiveChannelGrouping.adjacent(visible, id, -1)!!.name)
        }
        assertEquals("Canal", LiveChannelGrouping.adjacent(visible, "5", 1)!!.name)
        assertNull(LiveChannelGrouping.adjacent(visible.take(1), "2", 1))
        assertNull(LiveChannelGrouping.adjacent(emptyList(), "2", 1))
    }
    @Test fun providerSuperscriptFeedMarkersDoNotBecomeDifferentChannels() {
        val groups = LiveChannelGrouping.group(listOf(
            item(1, "PFC CLUBES HD¹", epg = null),
            item(2, "PFC CLUBES FHD²", epg = null),
            item(3, "PFC CLUBES HD", epg = null),
            item(4, "PFC 2 FHD", epg = null)
        ), "scope")
        assertEquals(listOf("PFC CLUBES", "PFC 2"), groups.map { it.name })
        assertEquals(3, groups.first().versions.size)
        assertEquals("PFC 2", LiveChannelGrouping.adjacent(groups, "1", 1)!!.name)
        assertEquals("Canal ¹", LiveChannelGrouping.parse("Canal ¹").name)
    }
    @Test fun emptyNamesStaySeparate() {
        assertEquals(3, grouped(item(1, ""), item(2, " "), item(3, "")).size)
    }
    @Test fun unknownResolutionAndCodecAreNeverInferred() {
        val version = grouped(item(1, "Canal")).single().versions.single()
        assertNull(version.resolution)
        assertNull(version.codec)
    }
    @Test fun mainStreamAndInputOrderDoNotChangeIdentity() {
        val items = listOf(item(1, "Canal SD"), item(3, "Canal FHD"), item(2, "Canal HD"))
        val sd = LiveChannelGrouping.group(items, "account", ChannelQuality.SD).single()
        val fhd = LiveChannelGrouping.group(items.reversed(), "account", ChannelQuality.FHD).single()
        assertEquals("1", sd.id)
        assertEquals("3", fhd.id)
        assertEquals(sd.stableKey, fhd.stableKey)
        assertEquals(sd.name, fhd.name)
        assertNotEquals(sd.stableKey, LiveChannelGrouping.group(items, "other").single().stableKey)
        assertTrue(sd.containsStream("2"))
    }
    @Test fun fallbackIsExactThenLowerThenHigherThenUnknown() {
        val versions = listOf(item(3, "Canal 4K"), item(1, "Canal SD"), item(2, "Canal HD"), item(4, "Canal"))
            .map(LiveChannelGrouping::version)
        assertEquals(2, LiveChannelGrouping.choose(versions, ChannelQuality.FHD).streamId)
        assertEquals(3, LiveChannelGrouping.choose(versions, ChannelQuality.AUTO).streamId)
        assertEquals(1, LiveChannelGrouping.choose(versions, ChannelQuality.SD).streamId)
        assertEquals(3, LiveChannelGrouping.choose(versions.filter { it.resolution?.rank == 4 }, ChannelQuality.HD).streamId)
        assertEquals(4, LiveChannelGrouping.choose(versions.filter { it.resolution == null }, ChannelQuality.HD).streamId)
    }
    @Test fun equivalentResolutionTieIsDeterministic() {
        val versions = listOf(item(8, "Canal HD"), item(2, "Canal 720p HEVC")).map(LiveChannelGrouping::version)
        assertEquals(2, LiveChannelGrouping.choose(versions, ChannelQuality.HD).streamId)
        assertEquals(2, LiveChannelGrouping.choose(versions.reversed(), ChannelQuality.HD).streamId)
    }
    @Test fun groupOrderFollowsVisibleSourceAndCircularNavigationSkipsVersions() {
        val items = listOf(item(8, "Z SD"), item(7, "Z FHD"), item(4, "A HD"), item(6, "A SD"))
        val groups = LiveChannelGrouping.group(items, "scope")
        assertEquals(listOf("Z", "A"), groups.map { it.name })
        assertEquals(listOf("8", "7", "4", "6"), LiveChannelGrouping.visible(items, "scope", false, ChannelQuality.AUTO).map { it.id })
        val current = groups.indexOfFirst { it.containsStream("8") }
        assertEquals(1, channelStepIndex(groups.size, current, 1))
        assertEquals(0, channelStepIndex(groups.size, 1, 1))
        assertEquals(1, channelStepIndex(groups.size, 0, -1))
        assertEquals(0, channelStepIndex(1, 0, 1))
    }
    @Test fun specialListNeverGainsVersionsFromAnotherList() {
        val source = PlaybackSourceRepository()
        val favorite = listOf(item(2, "Canal HD"))
        val token = source.capture("account/profile", favorite)
        assertEquals(listOf("2"), source.get(token, "account/profile")!!.map { it.id })
        assertNull(source.get(token, "other/profile"))
        assertEquals(listOf(2), LiveChannelGrouping.group(source.get(token, "account/profile")!!, "scope")
            .single().versions.map { it.streamId })
    }
    @Test fun emptyAndExpiredSourcesStayEmpty() {
        val source = PlaybackSourceRepository()
        val token = source.capture("a", emptyList())
        assertTrue(source.get(token, "a")!!.isEmpty())
        repeat(8) { source.capture("a", listOf(item(it + 1, "Canal"))) }
        assertNull(source.get(token, "a"))
    }
    @Test fun persistedIdsRemainRealAndFavoriteStateSurvivesQualitySelection() {
        val items = listOf(item(1, "Canal SD").copy(isFavorite = true), item(2, "Canal HD"))
        val sd = LiveChannelGrouping.group(items, "scope", ChannelQuality.SD).single()
        val hd = LiveChannelGrouping.group(items, "scope", ChannelQuality.HD).single()
        assertTrue(sd.isFavorite && hd.isFavorite)
        assertEquals("1", items.single { it.isFavorite }.id)
        assertTrue(sd.id.toInt() in sd.versions.map { it.streamId })
        assertTrue(hd.id.toInt() in hd.versions.map { it.streamId })
    }
}
