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
               {"name":"totalYards","label":"Total Yards","displayValue":"312"},
               {"name":"turnovers","label":"Turnovers","displayValue":"2"},
               {"name":"thirdDownEff","label":"3rd down efficiency","displayValue":"45% (3/7)"}
             ]},
             {"statistics":[
               {"name":"totalYards","label":"Total Yards","displayValue":"388"},
               {"name":"turnovers","label":"Turnovers","displayValue":"1"},
               {"name":"thirdDownEff","label":"3rd down efficiency","displayValue":"58% (4/7)"}
             ]}
           ],
           "players":[
             {"statistics":[{"name":"passing","labels":["C/ATT","YDS","TD","INT"],"athletes":[
               {"athlete":{"displayName":"Justin Fields","position":{"abbreviation":"QB"}},
                "stats":["22/31","248","2","1"]}
             ]}]},
             {"statistics":[{"name":"passing","labels":["C/ATT","YDS","TD","INT"],"athletes":[
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
        val yards = loaded.teamStats.first { it.label == "Total Yards" }
        assertEquals("312", yards.away)
        assertEquals("388", yards.home)
        assertTrue(yards.homeLeads, "the larger number leads")
        assertFalse(yards.awayLeads)
    }

    @Test
    fun `basketball stats show the provider's label, not the API name`() = runBlocking {
        val body = """
            {"boxscore":{"teams":[
              {"statistics":[{"name":"fieldGoalsMade-fieldGoalsAttempted","label":"FG","displayValue":"34-82"}]},
              {"statistics":[{"name":"fieldGoalsMade-fieldGoalsAttempted","label":"FG","displayValue":"30-78"}]}
            ]}}
        """.trimIndent()

        val stat = feed(body).summary(SportsCatalog.league("basketball/nba")!!, "1").teamStats.single()

        assertEquals("FG", stat.label)
    }

    @Test
    fun `a stat with no label is humanized rather than shown as an API name`() = runBlocking {
        val body = """
            {"boxscore":{"teams":[
              {"statistics":[{"name":"fieldGoalsMade-fieldGoalsAttempted","displayValue":"34-82"}]},
              {"statistics":[{"name":"fieldGoalsMade-fieldGoalsAttempted","displayValue":"30-78"}]}
            ]}}
        """.trimIndent()

        val stat = feed(body).summary(SportsCatalog.league("basketball/nba")!!, "1").teamStats.single()

        assertEquals("Field Goals Made / Field Goals Attempted", stat.label)
    }

    @Test
    fun `a percentage stat compares on its number, not its text`() = runBlocking {
        val loaded = feed(summary).summary(nfl, "1")

        val third = loaded.teamStats.first { it.label == "3rd down efficiency" }
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
    fun `scoring plays carry their points and the team that scored them`() = runBlocking {
        val body = """
            {"header":{"competitions":[{"competitors":[
              {"homeAway":"home","team":{"id":"24"}},
              {"homeAway":"away","team":{"id":"18"}}
            ]}]},
             "plays":[
               {"id":"p1","text":"Jump Ball","period":{"displayValue":"1st Quarter"},
                "clock":{"displayValue":"12:00"},"awayScore":0,"homeScore":0},
               {"id":"p2","text":"A. Player makes 3-pointer","period":{"displayValue":"1st Quarter"},
                "clock":{"displayValue":"11:40"},"awayScore":0,"homeScore":3,"scoringPlay":true,
                "scoreValue":3,"team":{"id":"24"}},
               {"id":"p3","text":"B. Player makes layup","period":{"displayValue":"1st Quarter"},
                "clock":{"displayValue":"11:10"},"awayScore":2,"homeScore":3,"scoringPlay":true}
             ]}
        """.trimIndent()

        val plays = feed(body).summary(SportsCatalog.league("basketball/nba")!!, "1").playByPlay

        assertNull(plays[0].points)
        assertEquals(3, plays[1].points)
        assertEquals("24", plays[1].scoringTeamId)
        // This play has no scoreValue or team: both come from the running score.
        assertEquals(2, plays[2].points)
        assertEquals("18", plays[2].scoringTeamId)
    }

    @Test
    fun `a football score with no tagged points still reads as points`() = runBlocking {
        val body = """
            {"header":{"competitions":[{"competitors":[
              {"homeAway":"home","team":{"id":"17"}},
              {"homeAway":"away","team":{"id":"19"}}
            ]}]},
             "plays":[
               {"id":"p1","text":"Kickoff","period":{"displayValue":"1st"},"clock":{"displayValue":"15:00"},"awayScore":0,"homeScore":0},
               {"id":"p2","text":"A.Borregales 22 yard field goal is GOOD","period":{"displayValue":"1st"},
                "clock":{"displayValue":"9:14"},"awayScore":0,"homeScore":3,"scoringPlay":true}
             ]}
        """.trimIndent()

        val play = feed(body).summary(nfl, "1").playByPlay.last()

        assertEquals(3, play.points)
        assertEquals("17", play.scoringTeamId)
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
        assertEquals(listOf("C/ATT", "YDS", "TD", "INT"), passing.players.first().stats.map { it.label })
        assertEquals("22/31", passing.players.first().stats.first().value)
    }

    @Test
    fun `a category with no name still shows its players`() = runBlocking {
        // NBA sends no category heading at all; only labels and the athletes.
        val body = """
            {"boxscore":{"players":[{"statistics":[{"labels":["PTS","REB","AST"],"athletes":[
              {"athlete":{"displayName":"A. Player","position":{"abbreviation":"G"}},"stats":["25","7","4"]}
            ]}]}]}}
        """.trimIndent()

        val category = feed(body).summary(SportsCatalog.league("basketball/nba")!!, "1").categories.single()

        assertEquals("Players", category.name)
        assertEquals(listOf("PTS", "REB", "AST"), category.players.single().stats.map { it.label })
    }

    @Test
    fun `a baseball leader shows its own number, not the whole season line`() {
        // The provider sends the full season line for every baseball category; only the rate or
        // count the category ranks matters in the leader row.
        assertEquals(
            ".331",
            sportsLeaderValue("Batting Average", "179-541, 53 HR, 30 2B, 114 RBI", 0.3308686912059784),
        )
        assertEquals("60", sportsLeaderValue("Home Runs", "147-596, 60 HR, 24 2B", 60.0))
        assertEquals("4707", sportsLeaderValue("Passing Yards", "4707", 4707.0))
    }

    @Test
    fun `season leaders and team news arrive with the summary`() = runBlocking {
        val summary = """
            {"header":{"season":{"year":2026,"type":2},"competitions":[{"competitors":[
              {"team":{"id":"18"}},{"team":{"id":"24"}}
            ]}]}}
        """.trimIndent()
        val leaders = """
            {"categories":[{"name":"pointsPerGame","displayName":"Points Per Game","leaders":[
              {"displayValue":"26.0","team":{"${'$'}ref":"http://sports.core.api.espn.com/v2/sports/basketball/leagues/nba/seasons/2026/teams/18"},
               "athlete":{"${'$'}ref":"http://sports.core.api.espn.com/v2/sports/basketball/leagues/nba/seasons/2026/athletes/1"}},
              {"displayValue":"25.0","team":{"${'$'}ref":"http://sports.core.api.espn.com/v2/sports/basketball/leagues/nba/seasons/2026/teams/24"},
               "athlete":{"${'$'}ref":"http://sports.core.api.espn.com/v2/sports/basketball/leagues/nba/seasons/2026/athletes/2"}}
            ]}]}
        """.trimIndent()
        val knicksNews = """
            {"articles":[{"id":7,"headline":"Knicks win again","description":"A short body.",
              "images":[{"type":"header","url":"https://cdn/header.jpg"}],
              "links":{"web":{"href":"https://espn/story"}}}]}
        """.trimIndent()
        val spursNews = """{"articles":[{"id":8,"headline":"Spurs update","description":"Another body."}]}"""
        val feed = EspnSportsFeed { url ->
            when {
                url.contains("team=18") -> knicksNews
                url.contains("team=24") -> spursNews
                url.contains("athletes/1") ->
                    """{"displayName":"Jalen Brunson","position":{"abbreviation":"G"},"headshot":{"href":"https://cdn/brunson.png"}}"""
                url.contains("athletes/2") ->
                    """{"displayName":"Victor Wembanyama","position":{"abbreviation":"F"}}"""
                url.contains("/leaders?") -> leaders
                else -> summary
            }
        }

        val loaded = feed.summary(SportsCatalog.league("basketball/nba")!!, "1")

        val knicks = loaded.leaders.first { it.teamId == "18" }
        assertEquals("Points Per Game", knicks.categories.single().name)
        assertEquals("Jalen Brunson", knicks.categories.single().leader.name)
        assertEquals("G", knicks.categories.single().leader.position)
        assertEquals("https://cdn/brunson.png", knicks.categories.single().leader.headshot)
        assertEquals("26.0", knicks.categories.single().leader.value)
        assertEquals("Victor Wembanyama", loaded.leaders.first { it.teamId == "24" }.categories.single().leader.name)

        val awayNews = loaded.news.first { it.teamId == "18" }.articles.single()
        assertEquals("Knicks win again", awayNews.headline)
        assertEquals("A short body.", awayNews.description)
        assertEquals("https://cdn/header.jpg", awayNews.image)
        assertEquals("https://espn/story", awayNews.link)
        assertEquals("Spurs update", loaded.news.first { it.teamId == "24" }.articles.single().headline)
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
