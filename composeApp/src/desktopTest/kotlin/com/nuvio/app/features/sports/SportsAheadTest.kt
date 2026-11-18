package com.nuvio.app.features.sports

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The My Sports "Steelers · 2nd in AFC North / 8 games ahead" rows: position notes from the standings
 * and each followed team's coming games, built from plain data.
 */
class SportsAheadTest {

    private fun team(id: String, short: String = id) = Team(
        id = id,
        name = "$id Name",
        shortName = short,
        abbreviation = short.take(3).uppercase(),
    )

    private fun event(
        id: String,
        startMs: Long,
        state: GameState,
        away: Team,
        home: Team,
    ) = SportsEvent(
        id = id,
        leagueId = "nfl",
        startMs = startMs,
        state = state,
        away = GameSide(away),
        home = GameSide(home),
    )

    private val steelers = team("pit", "Steelers")
    private val eagles = team("phi", "Eagles")
    private val broncos = team("den", "Broncos")

    @Test
    fun `channel-only filter keeps channel matches and followed-team games`() {
        val followed = event("followed", 1L, GameState.PRE, steelers, eagles)
        val other = event("other", 2L, GameState.PRE, broncos, eagles)
        val settings = SportsSettings(
            onlyGamesOnMyChannels = true,
            followedTeams = setOf(SportsFollows.teamKey("nfl", steelers.id)),
        )

        val visible = filterGamesByChannelAvailability(
            games = listOf(followed, other),
            settings = settings,
            matchedEventIds = setOf("other"),
        )

        assertEquals(listOf("followed", "other"), visible.map { it.id })
    }

    @Test
    fun `channel-only filter hides unmatched games when no team is followed`() {
        val matched = event("matched", 1L, GameState.PRE, steelers, eagles)
        val unmatched = event("unmatched", 2L, GameState.PRE, broncos, eagles)

        val visible = filterGamesByChannelAvailability(
            games = listOf(matched, unmatched),
            settings = SportsSettings(onlyGamesOnMyChannels = true),
            matchedEventIds = setOf("matched"),
        )

        assertEquals(listOf("matched"), visible.map { it.id })
    }

    @Test
    fun `channel-only filter is a no-op when disabled`() {
        val games = listOf(
            event("matched", 1L, GameState.PRE, steelers, eagles),
            event("unmatched", 2L, GameState.PRE, broncos, eagles),
        )

        assertEquals(games, filterGamesByChannelAvailability(games, SportsSettings(), emptySet()))
    }

    @Test
    fun `ordinals read the way positions are spoken`() {
        assertEquals("1st", ordinal(1))
        assertEquals("2nd", ordinal(2))
        assertEquals("3rd", ordinal(3))
        assertEquals("4th", ordinal(4))
        assertEquals("11th", ordinal(11))
        assertEquals("12th", ordinal(12))
        assertEquals("13th", ordinal(13))
        assertEquals("21st", ordinal(21))
        assertEquals("30th", ordinal(30))
    }

    @Test
    fun `a provider position note is used as it is`() {
        val row = Standing(team = steelers, note = "1st in AFC North", rank = 1, group = "AFC North")
        assertEquals("1st in AFC North", standingsNote(row))
    }

    @Test
    fun `a rank and division compose the note`() {
        val row = Standing(team = steelers, rank = 2, group = "AFC North")
        assertEquals("2nd in AFC North", standingsNote(row))
    }

    @Test
    fun `a row without a group or rank has no note`() {
        assertNull(standingsNote(Standing(team = steelers, rank = 0, group = "AFC North")))
        assertNull(standingsNote(Standing(team = steelers, rank = 2, group = null)))
        assertNull(standingsNote(Standing(team = steelers, note = "Playoffs", rank = 4, group = null)))
    }

    @Test
    fun `one games-ahead section per followed team, soonest first`() {
        val now = 1_000_000_000L
        val today = event("g1", now + 5_000, GameState.PRE, steelers, eagles)
        val later = event("g2", now + 90_000, GameState.PRE, broncos, steelers)
        val finished = event("g3", now - 10_000, GameState.POST, steelers, eagles)
        val live = event("g4", now - 1_000, GameState.LIVE, steelers, broncos)
        val settings = SportsSettings(followedTeams = setOf("nfl:pit", "nfl:den"))

        val sections = buildTeamAheadSections(
            settings = settings,
            teamsByLeague = mapOf("nfl" to listOf(steelers, eagles, broncos)),
            notesByTeamKey = mapOf("nfl:pit" to "2nd in AFC North"),
            gamesByLeague = mapOf("nfl" to listOf(later, finished, live, today)),
            nowMs = now,
        )

        assertEquals(2, sections.size)
        val steelersSection = sections.first { it.teamKey == "nfl:pit" }
        assertEquals(steelers, steelersSection.team)
        assertEquals("2nd in AFC North", steelersSection.note)
        assertEquals(listOf("g1", "g2"), steelersSection.games.map { it.id })

        val broncosSection = sections.first { it.teamKey == "nfl:den" }
        assertEquals(listOf("g2"), broncosSection.games.map { it.id })
        assertNull(broncosSection.note)
    }

    @Test
    fun `a followed team resolves from game sides when the team list has not loaded`() {
        val now = 1_000_000_000L
        val upcoming = event("g1", now + 5_000, GameState.PRE, steelers, eagles)
        val settings = SportsSettings(followedTeams = setOf("nfl:pit"))

        val sections = buildTeamAheadSections(
            settings = settings,
            teamsByLeague = emptyMap(),
            notesByTeamKey = emptyMap(),
            gamesByLeague = mapOf("nfl" to listOf(upcoming)),
            nowMs = now,
        )

        assertEquals(steelers, sections.single().team)
        assertEquals(listOf("g1"), sections.single().games.map { it.id })
    }

    @Test
    fun `a team with nothing coming keeps an empty section for the screen to drop`() {
        val now = 1_000_000_000L
        val upcoming = event("g1", now + 5_000, GameState.PRE, steelers, eagles)
        val settings = SportsSettings(followedTeams = setOf("nfl:pit", "nfl:den"))

        val sections = buildTeamAheadSections(
            settings = settings,
            teamsByLeague = emptyMap(),
            notesByTeamKey = emptyMap(),
            gamesByLeague = mapOf("nfl" to listOf(upcoming)),
            nowMs = now,
        )

        assertEquals(listOf("nfl:pit", "nfl:den"), sections.map { it.teamKey })
        // The screen only renders teams with games ahead; an empty section is dropped there.
        assertEquals(listOf("nfl:pit"), sections.filter { it.games.isNotEmpty() }.map { it.teamKey })
    }

    @Test
    fun `the caption reads one game or games`() {
        assertEquals("1 game ahead", gamesAheadCaption(1))
        assertEquals("8 games ahead", gamesAheadCaption(8))
    }
}
