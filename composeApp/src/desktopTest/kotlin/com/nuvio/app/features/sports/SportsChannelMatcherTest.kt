package com.nuvio.app.features.sports

import com.nuvio.app.features.iptv.IptvChannel
import com.nuvio.app.features.iptv.IptvEpgEntry
import com.nuvio.app.features.iptv.XmltvGuide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which channel a game is on. Every case here is a shape a real playlist produces: an event
 * channel named after the fixture, a network channel named after the broadcaster, a guide whose
 * programme names the teams, and providers that rename things.
 */
class SportsChannelMatcherTest {

    private val nfl = SportsCatalog.league("football/nfl")!!

    private fun channel(name: String, epgId: String? = null, num: Int = 1): IptvChannel =
        IptvChannel(
            uid = num,
            key = "key$num",
            num = num,
            name = name,
            categoryId = "sports",
            epgId = epgId,
        )

    private fun guide(entries: Map<String, List<IptvEpgEntry>>): XmltvGuide =
        XmltvGuide(url = "test", fetchedAt = 0L, programmes = entries, names = emptyMap())

    private fun entry(title: String, startMs: Long, endMs: Long, kind: String? = null) =
        IptvEpgEntry(title = title, startMs = startMs, endMs = endMs, kind = kind)

    private val kickoff = epochMillis(2026, 11, 17, 1, 0, 0)

    private fun bearsAtLions(network: String? = "FOX") = SportsEvent(
        id = "1",
        leagueId = nfl.id,
        startMs = kickoff,
        state = GameState.LIVE,
        away = GameSide(Team("1", "Chicago Bears", "Bears", "CHI"), score = "10"),
        home = GameSide(Team("2", "Detroit Lions", "Lions", "DET"), score = "14"),
        network = network,
    )

    @Test
    fun `a guide programme naming both teams wins`() {
        val game = bearsAtLions()
        val channel = channel("Some Channel", epgId = "some.id", num = 1)
        val tables = guide(mapOf("some.id" to listOf(entry("NFL: Bears @ Lions", kickoff, kickoff + 7200_000))))

        val matches = SportsChannelMatcher.match(game, nfl, listOf(channel), tables)

        assertEquals(1, matches.size)
        assertEquals(SportsChannelMatcher.Rank.Guide, matches.single().rank)
        assertEquals("NFL: Bears @ Lions", matches.single().reason)
    }

    @Test
    fun `an event channel named after the fixture is found without a guide`() {
        val channel = channel("NFL 03: Bears @ Lions", num = 1)

        val matches = SportsChannelMatcher.match(bearsAtLions(), nfl, listOf(channel), guide = null)

        assertEquals(SportsChannelMatcher.Rank.ChannelName, matches.single().rank)
    }

    @Test
    fun `the feed's network plus a sports programme is enough`() {
        // Strategy 2 is the guide entry naming the network the feed lists, tagged as sport: what a
        // game on a generic channel looks like. The channel name itself says nothing about FOX here.
        val game = bearsAtLions(network = "FOX")
        val channel = channel("Channel 17", epgId = "ch17", num = 1)
        val tables = guide(mapOf("ch17" to listOf(entry("FOX NFL Sunday", kickoff, kickoff + 3 * 3600_000, kind = "sport"))))

        val matches = SportsChannelMatcher.match(game, nfl, listOf(channel), tables)

        assertEquals(SportsChannelMatcher.Rank.GuideNetwork, matches.single().rank)
    }

    @Test
    fun `a channel named after the network ranks last`() {
        val channel = channel("US: FOX 5", num = 1)

        val matches = SportsChannelMatcher.match(bearsAtLions(network = "FOX"), nfl, listOf(channel), guide = null)

        assertEquals(SportsChannelMatcher.Rank.NetworkName, matches.single().rank)
    }

    @Test
    fun `guide evidence beats a channel that only names the network`() {
        val game = bearsAtLions(network = "FOX")
        val guided = channel("Channel A", epgId = "a", num = 1)
        val named = channel("US: FOX 5", num = 2)
        val tables = guide(mapOf("a" to listOf(entry("Bears vs Lions", kickoff, kickoff + 7200_000))))

        val matches = SportsChannelMatcher.match(game, nfl, listOf(named, guided), tables)

        assertEquals(listOf("key1", "key2"), matches.map { it.channel.key })
        assertEquals(SportsChannelMatcher.Rank.Guide, matches.first().rank)
    }

    @Test
    fun `a channel naming only one team is not a match`() {
        // Both must appear: "Bears" alone would put a highlights channel on the game.
        val matches = SportsChannelMatcher.match(bearsAtLions(), nfl, listOf(channel("Bears Classics")), guide = null)

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `a guide programme naming one team is not a match`() {
        val channel = channel("Channel A", epgId = "a")
        val tables = guide(mapOf("a" to listOf(entry("Bears Classics", kickoff, kickoff + 3600_000))))

        assertTrue(SportsChannelMatcher.match(bearsAtLions(), nfl, listOf(channel), tables).isEmpty())
    }

    @Test
    fun `an abbreviation in the channel name still matches`() {
        val matches = SportsChannelMatcher.match(bearsAtLions(), nfl, listOf(channel("GAME: CHI v DET")), guide = null)

        assertEquals(SportsChannelMatcher.Rank.ChannelName, matches.single().rank)
    }

    @Test
    fun `no channels means no matches, not a crash`() {
        assertTrue(SportsChannelMatcher.match(bearsAtLions(), nfl, emptyList(), guide = null).isEmpty())
    }

    @Test
    fun `a game with no network and no guide matches nothing by name alone`() {
        val game = bearsAtLions(network = null)

        assertTrue(SportsChannelMatcher.match(game, nfl, listOf(channel("US: FOX 5")), guide = null).isEmpty())
    }

    @Test
    fun `normalize folds case, accents and punctuation`() {
        assertEquals("la liga", SportsChannelMatcher.normalize("La Liga"))
        assertEquals("atletico madrid", SportsChannelMatcher.normalize("Atlético  Madrid!"))
        assertEquals("m and ms", SportsChannelMatcher.normalize("M&M's"))
        assertEquals("", SportsChannelMatcher.normalize("  "))
    }

    @Test
    fun `matching is stable within a rank`() {
        val channels = listOf(channel("NFL 01: Bears @ Lions", num = 1), channel("NFL 02: Bears @ Lions", num = 2))

        val matches = SportsChannelMatcher.match(bearsAtLions(), nfl, channels, guide = null)

        // The playlist's own order decides, not a hash of the name.
        assertEquals(listOf("key1", "key2"), matches.map { it.channel.key })
    }
}
