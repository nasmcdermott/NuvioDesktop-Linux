package com.nuvio.app.features.sports

import com.nuvio.app.features.iptv.IptvChannel
import com.nuvio.app.features.iptv.IptvChannelKind
import com.nuvio.app.features.iptv.IptvPlaylist
import com.nuvio.app.features.iptv.IptvPlaylistType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fix-match rules, scoped picks, channel picker search and renamed-channel resolution.
 */
class SportsFixMatchTest {

    private val playlistA = IptvPlaylist(id = "plA", type = IptvPlaylistType.Xtream, name = "Main IPTV")
    private val playlistB = IptvPlaylist(id = "plB", type = IptvPlaylistType.M3u, name = "Backup M3U")

    private fun available(
        playlist: IptvPlaylist,
        key: String,
        name: String,
        category: String = "UK | Sports",
        epgId: String? = null,
        uid: Int = 1,
    ) = SportsChannels.Available(
        playlist = playlist,
        channel = IptvChannel(uid = uid, key = key, num = uid, name = name, categoryId = "cat", epgId = epgId),
        categoryName = category,
        categoryRegion = "GB",
        kind = IptvChannelKind.Sport,
    )

    @Test
    fun `a block is scoped to one game, not to the channel`() {
        val key = SportsWatch.blockKey("401", "newsport:5")

        assertTrue(SportsWatch.blockKey("402", "newsport:5") != key)
        assertEquals("401", key.substringBefore('|'))
        assertEquals("newsport:5", key.substringAfter('|'))
    }

    @Test
    fun `a block key survives a channel id that contains a separator`() {
        val key = SportsWatch.blockKey("401", "pl|2:7")

        assertEquals("401", key.substringBefore('|'))
        assertEquals("pl|2:7", key.substringAfter('|'))
    }

    @Test
    fun `a pick survives the settings round-trip and counts across scopes`() {
        val settings = SportsSettings(
            picks = mapOf("401" to "plA::ch1"),
            teamChannels = mapOf("football/nfl:1" to "plA::ch2"),
            networkChannels = mapOf("espn plus" to "plA::ch3"),
            leagueChannels = mapOf("racing/f1" to "plA::ch4"),
        )
        assertEquals(4, settings.totalPickCount)
        assertEquals("plA::ch1", settings.picks["401"])

        val cleared = settings.copy(picks = settings.picks - "401")
        assertTrue(cleared.picks.isEmpty())
    }

    @Test
    fun `blocking and unblocking are inverse`() {
        val key = SportsWatch.blockKey("401", "newsport:5")
        val blocked = setOf(key)
        assertTrue(blocked.contains(key))
        assertTrue((blocked - key).isEmpty())
    }

    @Test
    fun `resolvePick matches playlist-scoped keys and survives a renamed channel with same epgId`() {
        val pool = listOf(
            available(playlistA, key = "old_other", name = "UK: Sky Sports Main Event", epgId = "sky.main", uid = 1),
            available(playlistB, key = "ch_f1_new", name = "UK: Sky Sports F1 UHD", epgId = "skysportsf1.uk", uid = 2),
        )

        val exact = assertNotNull(SportsChannels.resolvePick("plA::old_other", pool))
        assertEquals("plA", exact.playlist.id)

        val renamed = assertNotNull(SportsChannels.resolvePick("plB::m3u|skysportsf1.uk|Sky F1 HD", pool))
        assertEquals("ch_f1_new", renamed.channel.key)
    }

    @Test
    fun `channel picker search matches multi-word queries across name and category`() {
        val items = listOf(
            available(playlistA, "1", "UK: Sky Sports Formula 1 FHD", "UK | Motorsport", uid = 1),
            available(playlistA, "2", "US: ESPN FHD", "US | Sports", uid = 2),
            available(playlistB, "3", "CA: TSN 4 HD", "Canada Sports", uid = 3),
        )
        val index = SportsChannelPickerSearch.buildIndex(items)

        val skyHits = SportsChannelPickerSearch.search(index, "sky formula")
        assertEquals(listOf("1"), skyHits.map { it.channel.key })

        val canadaHits = SportsChannelPickerSearch.search(index, "tsn canada")
        assertEquals(listOf("3"), canadaHits.map { it.channel.key })
    }
}
