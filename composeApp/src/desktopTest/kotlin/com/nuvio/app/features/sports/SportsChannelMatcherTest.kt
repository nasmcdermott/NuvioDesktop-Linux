package com.nuvio.app.features.sports

import com.nuvio.app.features.iptv.IptvChannel
import com.nuvio.app.features.iptv.IptvEpgEntry
import com.nuvio.app.features.iptv.XmltvGuide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which channel a game is on. Covers EPG titles and descriptions, fixture channel names, club
 * nicknames, multi-network broadcast aliases, false-sibling exclusions, replay/banner filtering,
 * and stream quality / region ranking.
 */
class SportsChannelMatcherTest {

    private val nfl = SportsCatalog.league("football/nfl")!!
    private val epl = SportsCatalog.league("soccer/eng.1")!!

    private fun channel(name: String, epgId: String? = null, num: Int = 1, categoryId: String = "sports"): IptvChannel =
        IptvChannel(
            uid = num,
            key = "key$num",
            num = num,
            name = name,
            categoryId = categoryId,
            epgId = epgId,
        )

    private fun guide(entries: Map<String, List<IptvEpgEntry>>): XmltvGuide =
        XmltvGuide(url = "test", fetchedAt = 0L, programmes = entries, names = emptyMap())

    private fun entry(title: String, startMs: Long, endMs: Long, kind: String? = null, desc: String? = null) =
        IptvEpgEntry(title = title, startMs = startMs, endMs = endMs, kind = kind, desc = desc)

    private val kickoff = epochMillis(2026, 11, 17, 1, 0, 0)

    private fun bearsAtLions(
        network: String? = "FOX",
        broadcasts: List<String> = emptyList(),
    ) = SportsEvent(
        id = "1",
        leagueId = nfl.id,
        startMs = kickoff,
        state = GameState.LIVE,
        away = GameSide(Team("1", "Chicago Bears", "Bears", "CHI"), score = "10"),
        home = GameSide(Team("2", "Detroit Lions", "Lions", "DET"), score = "14"),
        network = network,
        broadcasts = broadcasts,
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
    fun `a guide programme with generic title and teams in description matches as Guide`() {
        val game = bearsAtLions(network = null)
        val ch = channel("Sports 1", epgId = "sp1", num = 1)
        val tables = guide(
            mapOf("sp1" to listOf(entry("NFL Football", kickoff, kickoff + 10_800_000, desc = "Chicago Bears at Detroit Lions from Ford Field."))),
        )

        val matches = SportsChannelMatcher.match(game, nfl, listOf(ch), tables)

        assertEquals(1, matches.size)
        assertEquals(SportsChannelMatcher.Rank.Guide, matches.single().rank)
    }

    @Test
    fun `an event channel named after the fixture is found without a guide`() {
        val channel = channel("NFL 03: Bears @ Lions", num = 1)

        val matches = SportsChannelMatcher.match(bearsAtLions(), nfl, listOf(channel), guide = null)

        assertEquals(SportsChannelMatcher.Rank.ChannelName, matches.single().rank)
    }

    @Test
    fun `the feed's network plus a sports programme is enough`() {
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
    fun `CBS SN is an alias for CBS Sports Network`() {
        val game = bearsAtLions(network = "CBS SN")
        val channels = listOf(
            channel("CBS Sports Network HD", num = 1),
            channel("CBS News HD", num = 2),
            channel("CBS SN", num = 3),
        )

        val matches = SportsChannelMatcher.match(game, nfl, channels, guide = null)

        assertEquals(listOf("key1", "key3"), matches.map { it.channel.key })
    }

    @Test
    fun `network aliases match and false sibling networks are excluded`() {
        val fs1Game = bearsAtLions(network = "FS1", broadcasts = listOf("FS1", "TSN"))
        val channels = listOf(
            channel("US: FOX News HD", num = 1),
            channel("US: FOX Movies", num = 2),
            channel("US: Fox Sports 1 FHD", num = 3),
            channel("CA: TSN 1 HD", num = 4),
            channel("##### USA SPORTS #####", num = 5),
        )

        val matches = SportsChannelMatcher.match(fs1Game, nfl, channels, guide = null)

        assertEquals(listOf("key3", "key4"), matches.map { it.channel.key })
    }

    @Test
    fun `espn does not false-match espn2 or espnews`() {
        val espnGame = bearsAtLions(network = "ESPN")
        val channels = listOf(
            channel("US: ESPN2 HD", num = 1),
            channel("US: ESPNews", num = 2),
            channel("US: ESPN Deportes", num = 3),
            channel("US: ESPN FHD", num = 4),
        )

        val matches = SportsChannelMatcher.match(espnGame, nfl, channels, guide = null)

        assertEquals(listOf("key4"), matches.map { it.channel.key })
    }

    @Test
    fun `a network channel whose guide shows a different matchup is excluded`() {
        val game = bearsAtLions(network = "FOX")
        val wrongRegionalFox = channel("US: FOX Dallas", epgId = "fox.dal", num = 1)
        val tables = guide(mapOf("fox.dal" to listOf(entry("NFL: Cowboys vs Giants", kickoff, kickoff + 10_800_000, kind = "sport"))))

        val matches = SportsChannelMatcher.match(game, nfl, listOf(wrongRegionalFox), tables)

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `club nicknames match soccer fixtures`() {
        val match = SportsEvent(
            id = "99",
            leagueId = epl.id,
            startMs = kickoff,
            state = GameState.LIVE,
            away = GameSide(Team("360", "Manchester United", "Man United", "MUN")),
            home = GameSide(Team("367", "Tottenham Hotspur", "Tottenham", "TOT")),
        )
        val ch = channel("EPL 01: Man Utd vs Spurs FHD", num = 1)

        val found = SportsChannelMatcher.match(match, epl, listOf(ch), guide = null)

        assertEquals(1, found.size)
        assertEquals(SportsChannelMatcher.Rank.ChannelName, found.single().rank)
    }

    @Test
    fun `quality and preferred region sort matches within the same rank`() {
        val game = bearsAtLions(network = "TNT Sports 1")
        val channels = listOf(
            channel("US: TNT Sports 1 SD", num = 1),
            channel("UK: TNT Sports 1 FHD", num = 2),
            channel("UK: TNT Sports 1 HD", num = 3),
        )

        val matches = SportsChannelMatcher.match(
            event = game,
            league = nfl,
            channels = channels,
            guide = null,
            bestQualityFirst = true,
            preferredRegion = "UK",
        )

        assertEquals(listOf("key2", "key3", "key1"), matches.map { it.channel.key })
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

        assertEquals(listOf("key1", "key2"), matches.map { it.channel.key })
    }
}
