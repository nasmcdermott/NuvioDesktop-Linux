package com.nuvio.app.features.sports

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The follow rules, which decide what My Sports shows.
 *
 * The two behaviours worth pinning: a followed league shows all of its games on its own, and
 * following a team does *not* quietly follow its league — that distinction is what stops My Sports
 * filling up with games nobody asked for.
 */
class SportsFollowsTest {

    private val nfl = "football/nfl"
    private val nba = "basketball/nba"

    private fun game(
        id: String,
        leagueId: String = nfl,
        awayId: String = "1",
        awayName: String = "Chicago Bears",
        homeId: String = "2",
        homeName: String = "Detroit Lions",
    ) = SportsEvent(
        id = id,
        leagueId = leagueId,
        startMs = 1_700_000_000_000L,
        state = GameState.PRE,
        away = GameSide(Team(awayId, awayName, awayName.substringAfterLast(' '), awayName.take(3))),
        home = GameSide(Team(homeId, homeName, homeName.substringAfterLast(' '), homeName.take(3))),
    )

    private fun settings(
        teams: Set<String> = emptySet(),
        leagues: Set<String> = emptySet(),
    ) = SportsSettings(followedTeams = teams, followedLeagues = leagues)

    @Test
    fun `a team key carries its league, so ids cannot collide across sports`() {
        assertEquals("football/nfl:1", SportsFollows.teamKey(nfl, "1"))
        assertEquals(nfl, SportsFollows.leagueOfTeamKey("football/nfl:1"))
        // The same team id in a different league is a different team.
        assertTrue(SportsFollows.teamKey(nfl, "1") != SportsFollows.teamKey(nba, "1"))
    }

    @Test
    fun `a malformed team key has no league`() {
        assertNull(SportsFollows.leagueOfTeamKey("noleague"))
        assertNull(SportsFollows.leagueOfTeamKey(":1"))
    }

    @Test
    fun `following a team puts only that team's games in My Sports`() {
        val s = settings(teams = setOf(SportsFollows.teamKey(nfl, "1")))
        val bearsGame = game("a")
        val otherGame = game("b", awayId = "3", awayName = "Green Bay Packers", homeId = "4", homeName = "Minnesota Vikings")

        assertTrue(SportsFollows.isInMySports(s, bearsGame))
        assertFalse(SportsFollows.isInMySports(s, otherGame))
        assertEquals(listOf("a"), SportsFollows.mySports(s, listOf(bearsGame, otherGame)).map { it.id })
    }

    @Test
    fun `following the away team is enough, and the home team too`() {
        val awayFollowed = settings(teams = setOf(SportsFollows.teamKey(nfl, "1")))
        val homeFollowed = settings(teams = setOf(SportsFollows.teamKey(nfl, "2")))

        assertTrue(SportsFollows.isInMySports(awayFollowed, game("a")))
        assertTrue(SportsFollows.isInMySports(homeFollowed, game("a")))
    }

    @Test
    fun `a followed league shows every game in it`() {
        val s = settings(leagues = setOf(nfl))
        val games = listOf(game("a"), game("b", awayId = "9", homeId = "10"))

        assertEquals(listOf("a", "b"), SportsFollows.mySports(s, games).map { it.id })
    }

    @Test
    fun `following a team does not follow its league`() {
        // The rule that keeps My Sports from flooding: one followed team, one team's games.
        val s = settings(teams = setOf(SportsFollows.teamKey(nfl, "1")))

        assertFalse(SportsFollows.isLeagueFollowed(s, nfl), "a team follow must not imply a league follow")
        assertEquals(1, SportsFollows.count(s))
    }

    @Test
    fun `a team follow in another league does not leak`() {
        // Team ids are only unique within a league, so the league must be part of the match.
        val s = settings(teams = setOf(SportsFollows.teamKey(nba, "1")))

        assertFalse(SportsFollows.isInMySports(s, game("a", leagueId = nfl)))
    }

    @Test
    fun `following a league and a team in it is still one game, listed once`() {
        val s = settings(teams = setOf(SportsFollows.teamKey(nfl, "1")), leagues = setOf(nfl))

        val mine = SportsFollows.mySports(s, listOf(game("a")))

        assertEquals(1, mine.size)
    }

    @Test
    fun `nothing followed is empty, which is what the first-open picker keys off`() {
        assertTrue(SportsFollows.isEmpty(SportsSettings()))
        assertFalse(SportsFollows.isEmpty(settings(leagues = setOf(nfl))))
        assertFalse(SportsFollows.isEmpty(settings(teams = setOf(SportsFollows.teamKey(nfl, "1")))))
    }

    @Test
    fun `count covers both kinds`() {
        val s = settings(teams = setOf(SportsFollows.teamKey(nfl, "1"), SportsFollows.teamKey(nfl, "2")), leagues = setOf(nba))

        assertEquals(3, SportsFollows.count(s))
    }

    @Test
    fun `a league-only game is not marked as a followed team's game`() {
        // The star on a card means "one of your teams is playing", not merely "this is in My Sports".
        val leagueOnly = settings(leagues = setOf(nfl))
        val teamFollowed = settings(teams = setOf(SportsFollows.teamKey(nfl, "1")))

        assertFalse(SportsFollows.isFollowedTeamGame(leagueOnly, game("a")))
        assertTrue(SportsFollows.isFollowedTeamGame(teamFollowed, game("a")))
    }

    @Test
    fun `My Sports keeps the order it was given`() {
        val s = settings(leagues = setOf(nfl))
        val games = listOf(game("z"), game("a"), game("m"))

        assertEquals(listOf("z", "a", "m"), SportsFollows.mySports(s, games).map { it.id })
    }

    @Test
    fun `follows survive a settings round trip`() {
        val original = SportsSettings(
            followedTeams = setOf(SportsFollows.teamKey(nfl, "1")),
            followedLeagues = setOf(nba),
            setUp = true,
        )
        val encoded = kotlinx.serialization.json.Json.encodeToString(SportsSettings.serializer(), original)
        val decoded = kotlinx.serialization.json.Json.decodeFromString(SportsSettings.serializer(), encoded)

        assertEquals(original.followedTeams, decoded.followedTeams)
        assertEquals(original.followedLeagues, decoded.followedLeagues)
        assertTrue(decoded.setUp)
    }
}
