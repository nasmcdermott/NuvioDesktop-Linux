package com.nuvio.app.features.sports

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The box score: team comparison numbers, per-player categories and the scoring summary.
 *
 * The payload shape is the provider's own and is not guaranteed, so every case here is one that
 * arrives half-empty in practice — a league with no `players` block, a team missing a stat the other
 * has, a stat whose value is a percentage string rather than a number.
 */
class EspnBoxScoreTest {

    private val nfl = SportsCatalog.league("football/nfl")!!

    private fun feed(body: String) = EspnSportsFeed { body }

    private val summary = """
        {"header":{"competitions":[{"venue":{"fullName":"Ford Field"}}],"standings":{"entries":[]}},
         "boxscore":{
           "teams":[
             {"statistics":[
               {"name":"totalYards","displayValue":"312"},
               {"name":"turnovers","displayValue":"2"},
               {"name":"thirdDownEff","displayValue":"45% (3/7)"}
             ]},
             {"statistics":[
               {"name":"totalYards","displayValue":"388"},
               {"name":"turnovers","displayValue":"1"},
               {"name":"thirdDownEff","displayValue":"58% (4/7)"}
             ]}
           ],
           "players":[
             {"statistics":[{"name":"passing","athletes":[
               {"athlete":{"displayName":"Justin Fields","position":{"abbreviation":"QB"}},
                "stats":["22/31","248","2","1"]}
             ]}]},
             {"statistics":[{"name":"passing","athletes":[
               {"athlete":{"displayName":"Jared Goff","position":{"abbreviation":"QB"}},
                "stats":["25/33","301","3","0"]}
             ]}]}
           ]
         },
         "scoringPlays":[
           {"period":{"displayValue":"1st"},"clock":{"displayValue":"8:12"},
            "text":"Jared Goff 12 yd TD pass","awayScore":"0","homeScore":"7"}
         ]}
    """.trimIndent()

    @Test
    fun `team stats pair both sides by stat name`() = runBlocking {
        val loaded = feed(summary).summary(nfl, "1")

        assertEquals(3, loaded.teamStats.size)
        val yards = loaded.teamStats.first { it.label == "TotalYards" }
        assertEquals("312", yards.away)
        assertEquals("388", yards.home)
        assertTrue(yards.homeLeads, "the larger number leads")
        assertFalse(yards.awayLeads)
    }

    @Test
    fun `a percentage stat compares on its number, not its text`() = runBlocking {
        val loaded = feed(summary).summary(nfl, "1")

        val third = loaded.teamStats.first { it.label == "ThirdDownEff" }
        // 58% beats 45%; compared as text, "5" > "4" would agree here but "100%" vs "99%" would not.
        assertTrue(third.homeLeads)
    }

    @Test
    fun `a stat only one side has shows a dash rather than shifting the row`() = runBlocking {
        val body = """
            {"boxscore":{"teams":[
              {"statistics":[{"name":"sacks","displayValue":"3"}]},
              {"statistics":[{"name":"other","displayValue":"1"}]}
            ]}}
        """.trimIndent()

        val stat = feed(body).summary(nfl, "1").teamStats.single()

        assertEquals("3", stat.away)
        assertEquals("—", stat.home)
        assertFalse(stat.awayLeads, "a missing value is not a lead")
    }

    @Test
    fun `one team alone is not a comparison`() = runBlocking {
        val body = """{"boxscore":{"teams":[{"statistics":[{"name":"sacks","displayValue":"3"}]}]}}"""

        assertTrue(feed(body).summary(nfl, "1").teamStats.isEmpty())
    }

    @Test
    fun `player categories fold both teams into one heading`() = runBlocking {
        val loaded = feed(summary).summary(nfl, "1")

        assertEquals(1, loaded.categories.size, "both sides' passing merges into one category")
        val passing = loaded.categories.single()
        assertEquals("Passing", passing.name)
        assertEquals(listOf("Justin Fields", "Jared Goff"), passing.players.map { it.name })
        assertEquals("QB", passing.players.first().position)
        assertEquals("22/31 248 2 1", passing.players.first().headline)
    }

    @Test
    fun `scoring and the venue survive alongside the box score`() = runBlocking {
        val loaded = feed(summary).summary(nfl, "1")

        assertEquals("Ford Field", loaded.venue)
        assertEquals(1, loaded.scoring.size)
        assertEquals("Jared Goff 12 yd TD pass", loaded.scoring.single().text)
    }

    @Test
    fun `flat basketball play list is parsed in provider order`() = runBlocking {
        val nba = SportsCatalog.league("basketball/nba")!!
        val body = """
            {"plays":[
              {"id":"p1","text":"Jump Ball","period":{"number":1,"displayValue":"1st Quarter"},
               "clock":{"displayValue":"12:00"},"awayScore":0,"homeScore":0,"scoringPlay":false},
              {"id":"p2","text":"A. Player makes layup","period":{"number":1,"displayValue":"1st Quarter"},
               "clock":{"displayValue":"11:40"},"awayScore":2,"homeScore":0,"scoringPlay":true}
            ]}
        """.trimIndent()

        val plays = feed(body).summary(nba, "1").playByPlay

        assertEquals(listOf("p1", "p2"), plays.map { it.id })
        assertEquals("1st Quarter", plays.last().period)
        assertEquals("11:40", plays.last().clock)
        assertTrue(plays.last().scoring)
        assertEquals("2", plays.last().awayScore)
    }

    @Test
    fun `summary parses odds from pickcenter when scoreboard has none`() = runBlocking {
        val body = """
            {"pickcenter":[{"provider":{"name":"DraftKings"},"details":"CLE -4.5","overUnder":239.5,
              "awayTeamOdds":{"moneyLine":155},"homeTeamOdds":{"moneyLine":-185}}]}
        """.trimIndent()

        val odds = feed(body).summary(SportsCatalog.league("basketball/nba")!!, "1").moneyline

        assertEquals("DraftKings", odds?.provider)
        assertEquals("155", odds?.away)
        assertEquals("-185", odds?.home)
        assertEquals("CLE -4.5", odds?.spread)
        assertEquals("239.5", odds?.overUnder)
    }

    @Test
    fun `play wallclock is retained as an epoch timestamp`() = runBlocking {
        val body = """
            {"plays":[{"id":"p1","text":"Jump Ball","period":{"displayValue":"1st Quarter"},
              "clock":{"displayValue":"12:00"},"wallclock":"2026-11-19T00:10:26Z"}]}
        """.trimIndent()

        val play = feed(body).summary(SportsCatalog.league("basketball/nba")!!, "1").playByPlay.single()

        assertEquals(epochMillis(2026, 11, 19, 0, 10, 26), play.wallclockMs)
    }

    @Test
    fun `NFL drive plays flatten in chronological order and include the current drive`() = runBlocking {
        val body = """
            {"drives":{"previous":[{"plays":[
              {"id":"p1","text":"Kickoff","period":{"number":1},"clock":{"displayValue":"15:00"},"awayScore":0,"homeScore":0},
              {"id":"p2","text":"Touchdown","period":{"number":1},"clock":{"displayValue":"8:00"},"awayScore":0,"homeScore":7,"scoringPlay":true}
            ]}],"current":{"plays":[
              {"id":"p3","shortDescription":"Run","period":{"number":2},"clock":{"displayValue":"12:00"},"awayScore":0,"homeScore":7}
            ]}}}
        """.trimIndent()

        val plays = feed(body).summary(nfl, "1").playByPlay

        assertEquals(listOf("p1", "p2", "p3"), plays.map { it.id })
        assertEquals("Period 2", plays.last().period)
        assertEquals("Run", plays.last().text)
        assertEquals("7", plays.last().homeScore)
    }

    @Test
    fun `a summary with no box score still parses`() = runBlocking {
        // A league the provider carries scores for but no players; the page must still draw.
        val loaded = feed("""{"header":{}}""").summary(nfl, "1")

        assertTrue(loaded.teamStats.isEmpty())
        assertTrue(loaded.categories.isEmpty())
        assertTrue(loaded.scoring.isEmpty())
    }

    @Test
    fun `an athlete with no name is skipped rather than shown blank`() = runBlocking {
        val body = """
            {"boxscore":{"players":[{"statistics":[{"name":"passing","athletes":[
              {"stats":["1","2"]},
              {"athlete":{"displayName":"Real Player"},"stats":["3"]}
            ]}]}]}}
        """.trimIndent()

        val players = feed(body).summary(nfl, "1").categories.single().players

        assertEquals(listOf("Real Player"), players.map { it.name })
    }

    @Test
    fun `a summary that cannot be fetched is null, so the page can say so`() = runBlocking {
        val failing = EspnSportsFeed { throw java.io.IOException("offline") }

        assertNull(failing.let { runCatching { it.summary(nfl, "1") }.getOrNull() })
    }

    @Test
    fun `the box score round-trips through serialization`() = runBlocking {
        val loaded = feed(summary).summary(nfl, "1")
        val json = kotlinx.serialization.json.Json

        val decoded = json.decodeFromString(
            GameSummary.serializer(),
            json.encodeToString(GameSummary.serializer(), loaded),
        )

        assertNotNull(decoded.teamStats.firstOrNull())
        assertEquals(loaded.teamStats, decoded.teamStats)
        assertEquals(loaded.categories, decoded.categories)
    }
}
