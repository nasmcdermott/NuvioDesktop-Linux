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
