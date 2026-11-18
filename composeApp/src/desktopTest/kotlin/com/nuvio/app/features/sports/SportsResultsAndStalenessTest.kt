package com.nuvio.app.features.sports

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stage 4a: results appear, and a stale page says so.
 *
 * The change that matters here is that finished games are no longer dropped. For a followed team
 * the last result is the thing people open the page for, so the split and its ordering are pinned.
 */
class SportsResultsAndStalenessTest {

    private fun game(id: String, state: GameState, startMs: Long) = SportsEvent(
        id = id,
        leagueId = "football/nfl",
        startMs = startMs,
        state = state,
        away = GameSide(Team("1", "Chicago Bears", "Bears", "CHI"), score = "17"),
        home = GameSide(Team("2", "Detroit Lions", "Lions", "DET"), score = "20"),
    )

    @Test
    fun `finished games are results, not dropped`() {
        val split = splitDay(listOf(game("a", GameState.POST, 1_000L)))

        assertEquals(listOf("a"), split.results.map { it.id })
        assertTrue(split.live.isEmpty())
        assertTrue(split.upcoming.isEmpty())
    }

    @Test
    fun `results run most recent first, the other sections run soonest first`() {
        val split = splitDay(
            listOf(
                game("old", GameState.POST, 1_000L),
                game("new", GameState.POST, 9_000L),
                game("soon", GameState.PRE, 1_000L),
                game("later", GameState.PRE, 9_000L),
            ),
        )

        // The last result is the interesting one; the next game is what matters in the other two.
        assertEquals(listOf("new", "old"), split.results.map { it.id })
        assertEquals(listOf("soon", "later"), split.upcoming.map { it.id })
    }

    @Test
    fun `a day of nothing is empty, which drives the empty state`() {
        assertTrue(splitDay(emptyList()).isEmpty)
        // One finished game is not nothing: a past day with results must not say "No games".
        assertTrue(!splitDay(listOf(game("a", GameState.POST, 1_000L))).isEmpty)
    }

    @Test
    fun `every game lands in exactly one section`() {
        val games = listOf(
            game("l", GameState.LIVE, 1L),
            game("p", GameState.PRE, 2L),
            game("f", GameState.POST, 3L),
        )

        val split = splitDay(games)

        assertEquals(games.map { it.id }.toSet(), (split.live + split.upcoming + split.results).map { it.id }.toSet())
        assertEquals(3, split.live.size + split.upcoming.size + split.results.size)
    }

    @Test
    fun `a followed team's finished game is still in My Sports`() {
        // The end-to-end shape of the request: favourite a team, see its recent result.
        val settings = SportsSettings(followedTeams = setOf(SportsFollows.teamKey("football/nfl", "1")))
        val finished = game("a", GameState.POST, 1_000L)

        assertTrue(SportsFollows.isInMySports(settings, finished))
        assertEquals(listOf("a"), splitDay(SportsFollows.mySports(settings, listOf(finished))).results.map { it.id })
    }

    @Test
    fun `staleness reads in whole minutes, then hours`() {
        val now = 1_000_000_000_000L
        fun age(minutesAgo: Long) = relativeAge(now - minutesAgo * 60_000L, now)

        assertEquals("just now", age(0))
        assertEquals("1 minute ago", age(1))
        assertEquals("42 minutes ago", age(42))
        assertEquals("1 hour ago", age(60))
        assertEquals("3 hours ago", age(180))
    }

    @Test
    fun `never fetched has no age to show`() {
        // The note then reads plainly "Could not refresh", rather than "showing null".
        assertNull(relativeAge(0L))
    }

    @Test
    fun `a clock that moved backwards does not read as a negative age`() {
        val now = 1_000_000_000_000L
        assertEquals("just now", relativeAge(now + 60_000L, now))
    }
}
