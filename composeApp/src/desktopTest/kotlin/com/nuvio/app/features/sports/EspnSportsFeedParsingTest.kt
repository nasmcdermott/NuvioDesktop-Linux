package com.nuvio.app.features.sports

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The feed's parsing, against payloads shaped like the real ones.
 *
 * These are trimmed from live responses rather than invented: the point of the test is that the
 * field paths are right, and a made-up payload would only test that the code agrees with itself.
 * The feed is unofficial and changes shape, so every case here is one that has a plausible
 * half-missing variant.
 */
class EspnSportsFeedParsingTest {

    private fun feed(body: String, onUrl: (String) -> Unit = {}): EspnSportsFeed =
        EspnSportsFeed { url ->
            onUrl(url)
            body
        }

    private val nfl = SportsCatalog.league("football/nfl")!!

    @Test
    fun `scoreboard reads both sides, scores and a live clock`() = runBlocking {
        val body = """
            {"events":[{
              "id":"401671814","date":"2026-11-17T01:15:00Z",
              "competitions":[{
                "status":{"period":3,"displayClock":"9:42",
                  "type":{"state":"in","completed":false,"shortDetail":"3rd · 9:42"}},
                "broadcasts":[{"names":["ESPN"]}],
                "competitors":[
                  {"homeAway":"away","score":"24","winner":false,
                   "records":[{"summary":"4-6"}],
                   "team":{"id":"9","displayName":"Charleston So","shortDisplayName":"Charleston So",
                           "abbreviation":"CHSO","color":"0d3b66"}},
                  {"homeAway":"home","score":"35","winner":false,
                   "records":[{"summary":"7-3"}],
                   "team":{"id":"10","displayName":"East Carolina","shortDisplayName":"E Carolina",
                           "abbreviation":"ECU"}}
                ]
              }]
            }]}
        """.trimIndent()

        val games = feed(body).scoreboard(nfl, "20261117", "20261117")

        assertEquals(1, games.size)
        val game = games.single()
        assertEquals("401671814", game.id)
        assertEquals(GameState.LIVE, game.state)
        assertEquals("3rd · 9:42", game.status)
        assertEquals("Charleston So", game.away.team.name)
        assertEquals("24", game.away.score)
        assertEquals("4-6", game.away.record)
        assertEquals("East Carolina", game.home.team.name)
        assertEquals("35", game.home.score)
        assertEquals("ESPN", game.network)
        assertEquals(0xFF0D3B66L, game.away.team.color)
        assertTrue(game.isLive && game.hasScore)
        // 2026-11-17T01:15:00Z
        assertEquals(epochMillis(2026, 11, 17, 1, 15, 0), game.startMs)
    }

    @Test
    fun `a finished game is POST and keeps its final status`() = runBlocking {
        val body = """
            {"events":[{"id":"1","date":"2026-11-15T18:00:00Z","competitions":[{
              "status":{"period":4,"displayClock":"0:00",
                "type":{"state":"post","completed":true,"shortDetail":"Final"}},
              "competitors":[
                {"homeAway":"away","score":"17","team":{"id":"1","displayName":"A","shortDisplayName":"A","abbreviation":"A"}},
                {"homeAway":"home","score":"20","winner":true,"team":{"id":"2","displayName":"B","shortDisplayName":"B","abbreviation":"B"}}
              ]}]}]}
        """.trimIndent()

        val game = feed(body).scoreboard(nfl, "20261115", "20261115").single()

        assertEquals(GameState.POST, game.state)
        assertEquals("Final", game.status)
        assertTrue(game.home.winner)
    }

    @Test
    fun `a scheduled game with no clock is PRE and has no score`() = runBlocking {
        val body = """
            {"events":[{"id":"9","date":"2026-11-20T20:45:00Z","competitions":[{
              "status":{"period":0,"type":{"state":"pre","completed":false,"shortDetail":"8:45 PM"}},
              "competitors":[
                {"homeAway":"away","team":{"id":"1","displayName":"A","shortDisplayName":"A","abbreviation":"A"}},
                {"homeAway":"home","team":{"id":"2","displayName":"B","shortDisplayName":"B","abbreviation":"B"}}
              ]}]}]}
        """.trimIndent()

        val game = feed(body).scoreboard(nfl, "20261120", "20261120").single()

        assertEquals(GameState.PRE, game.state)
        assertNull(game.away.score)
        assertTrue(!game.hasScore)
    }

    @Test
    fun `a one-day range sends one date and a range sends two`() = runBlocking {
        val seen = mutableListOf<String>()
        val feed = feed("""{"events":[]}""") { seen += it }

        feed.scoreboard(nfl, "20261117", "20261117")
        feed.scoreboard(nfl, "20261117", "20261119")

        assertTrue(seen[0].contains("dates=20261117&"), "single day: ${seen[0]}")
        assertTrue(seen[1].contains("dates=20261117-20261119"), "range: ${seen[1]}")
        assertTrue(seen[0].startsWith("https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard"))
    }

    @Test
    fun `a competition with no competitors is skipped, not fatal`() = runBlocking {
        val body = """
            {"events":[
              {"id":"1","date":"2026-11-17T01:00:00Z","competitions":[]},
              {"id":"2","date":"2026-11-17T02:00:00Z","competitions":[{
                "status":{"type":{"state":"in","completed":false}},
                "competitors":[
                  {"homeAway":"away","score":"1","team":{"id":"a","displayName":"A","shortDisplayName":"A","abbreviation":"A"}},
                  {"homeAway":"home","score":"2","team":{"id":"b","displayName":"B","shortDisplayName":"B","abbreviation":"B"}}
                ]}]}
            ]}
        """.trimIndent()

        val games = feed(body).scoreboard(nfl, "20261117", "20261117")

        assertEquals(listOf("2"), games.map { it.id })
    }

    @Test
    fun `an empty events array is an empty list, not a failure`() = runBlocking {
        assertTrue(feed("""{"events":[]}""").scoreboard(nfl, "20261117", "20261117").isEmpty())
        assertTrue(feed("""{}""").scoreboard(nfl, "20261117", "20261117").isEmpty())
    }

    @Test
    fun `a transport failure throws so the caller can keep its cache`() = runBlocking {
        val failing = EspnSportsFeed { url -> throw SportsFeedException(url, java.io.IOException("offline")) }

        val threw = try {
            failing.scoreboard(nfl, "20261117", "20261117")
            false
        } catch (e: SportsFeedException) {
            true
        }
        assertTrue(threw, "a failed request must reach the caller, which keeps its cached day")
    }

    @Test
    fun `teams are read from the nested sports-leagues container`() = runBlocking {
        val body = """
            {"sports":[{"leagues":[{"teams":[
              {"team":{"id":"1","displayName":"Chicago Bears","shortDisplayName":"Bears",
                       "abbreviation":"CHI","logos":[{"href":"https://logo/chi.png"}],"color":"0B162A"}},
              {"team":{"id":"2","displayName":"Detroit Lions","shortDisplayName":"Lions","abbreviation":"DET"}}
            ]}]}]}
        """.trimIndent()

        val teams = feed(body).teams(nfl)

        assertEquals(2, teams.size)
        assertEquals("Chicago Bears", teams[0].name)
        assertEquals("Bears", teams[0].shortName)
        assertEquals("https://logo/chi.png", teams[0].logo)
        assertEquals(0xFF0B162AL, teams[0].color)
        assertNull(teams[1].logo)
    }

    @Test
    fun `scoring plays and the venue come from the summary`() = runBlocking {
        val body = """
            {"header":{"competitions":[{"venue":{"fullName":"Dowdy-Ficklen Stadium"}}],
              "standings":{"entries":[]}},
             "scoringPlays":[
               {"period":{"displayValue":"3rd"},"clock":{"displayValue":"9:42"},
                "text":"Jayla Hearp makes free throws","awayScore":"24","homeScore":"35"}
             ]}
        """.trimIndent()

        val summary = feed(body).summary(nfl, "401671814")

        assertEquals("Dowdy-Ficklen Stadium", summary.venue)
        assertEquals(1, summary.scoring.size)
        assertEquals("3rd", summary.scoring.single().period)
        assertEquals("Jayla Hearp makes free throws", summary.scoring.single().text)
    }

    @Test
    fun `standings walk nested children for divisions`() = runBlocking {
        val body = """
            {"children":[{"name":"AFC East","standings":{"entries":[
              {"team":{"id":"2","displayName":"Buffalo Bills","shortDisplayName":"Bills","abbreviation":"BUF"},
               "note":{"description":"1st in AFC East"},
               "stats":[{"shortName":"W","displayValue":"9"},{"shortName":"L","displayValue":"2"}]}
            ]}}]}
        """.trimIndent()

        val table = feed(body).standings(nfl)

        assertEquals(1, table.size)
        assertEquals("Buffalo Bills", table.single().team.name)
        assertEquals("1st in AFC East", table.single().note)
        assertEquals(listOf("W" to "9", "L" to "2"), table.single().values.map { it.label to it.value })
    }

    @Test
    fun `a league with no table gives an empty list`() = runBlocking {
        assertTrue(feed("""{}""").standings(nfl).isEmpty())
    }

    @Test
    fun `a malformed date does not throw`() = runBlocking {
        val body = """
            {"events":[{"id":"1","date":"not-a-date","competitions":[{
              "status":{"type":{"state":"pre","completed":false}},
              "competitors":[
                {"homeAway":"away","team":{"id":"a","displayName":"A","shortDisplayName":"A","abbreviation":"A"}},
                {"homeAway":"home","team":{"id":"b","displayName":"B","shortDisplayName":"B","abbreviation":"B"}}
              ]}]}]}
        """.trimIndent()

        assertEquals(0L, feed(body).scoreboard(nfl, "20261117", "20261117").single().startMs)
    }
}
