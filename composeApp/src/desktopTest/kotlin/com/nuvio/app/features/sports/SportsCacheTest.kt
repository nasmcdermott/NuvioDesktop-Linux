package com.nuvio.app.features.sports

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The games cache: what survives a restart, and what must not survive a corrupt file.
 *
 * The test task points `nuvio.desktop.dataDir` at a throwaway folder and clears it first, so this
 * writes into `build/test-data-dir` and never into the real install.
 */
class SportsCacheTest {

    private val leagueId = "football/nfl"

    @BeforeTest
    fun clean() {
        SportsCache.clearAll()
    }

    @AfterTest
    fun tidy() {
        SportsCache.clearAll()
    }

    private fun game(id: String) = SportsEvent(
        id = id,
        leagueId = leagueId,
        startMs = 1_700_000_000_000L,
        state = GameState.LIVE,
        status = "3rd · 9:42",
        away = GameSide(Team("1", "Chicago Bears", "Bears", "CHI"), score = "10", record = "4-6"),
        home = GameSide(Team("2", "Detroit Lions", "Lions", "DET"), score = "14", record = "7-3"),
        network = "FOX",
    )

    @Test
    fun `games round trip with their fetch time`() {
        val games = listOf(game("1"), game("2"))

        SportsCache.writeGames(leagueId, "20261117", "20261117", games, 42L)
        val read = assertNotNull(SportsCache.readGames(leagueId, "20261117", "20261117"))

        assertEquals(42L, read.fetchedAtMs)
        assertEquals(listOf("1", "2"), read.games.map { it.id })
        assertEquals("Detroit Lions", read.games.first().home.team.name)
        assertEquals("14", read.games.first().home.score)
        assertEquals(GameState.LIVE, read.games.first().state)
        assertEquals("FOX", read.games.first().network)
    }

    @Test
    fun `a day that was never fetched reads as nothing`() {
        assertNull(SportsCache.readGames(leagueId, "20260101", "20260101"))
    }

    @Test
    fun `a different date range is a different entry`() {
        SportsCache.writeGames(leagueId, "20261117", "20261117", listOf(game("1")), 1L)

        assertNull(SportsCache.readGames(leagueId, "20261118", "20261118"))
        assertNull(SportsCache.readGames(leagueId, "20261117", "20261119"))
    }

    @Test
    fun `an empty day is cached as an empty day, not as a miss`() {
        // The distinction matters: "no games today" is a real answer worth showing without a refetch.
        SportsCache.writeGames(leagueId, "20261225", "20261225", emptyList(), 7L)

        val read = assertNotNull(SportsCache.readGames(leagueId, "20261225", "20261225"))
        assertTrue(read.games.isEmpty())
        assertEquals(7L, read.fetchedAtMs)
    }

    @Test
    fun `a corrupt file reads as nothing rather than throwing`() {
        SportsCache.writeGames(leagueId, "20261117", "20261117", listOf(game("1")), 1L)
        val cacheRoot = java.nio.file.Path.of(System.getProperty("nuvio.desktop.dataDir"))
        val written = Files.walk(cacheRoot).use { stream ->
            stream.filter { it.fileName.toString().startsWith("games_") && it.fileName.toString().endsWith(".gz") }
                .findFirst().orElse(null)
        }
        assertTrue(written != null, "the cache file should exist before it is corrupted")
        Files.write(written, byteArrayOf(1, 2, 3, 4))

        assertNull(SportsCache.readGames(leagueId, "20261117", "20261117"))
    }

    @Test
    fun `teams round trip separately from games`() {
        val teams = listOf(Team("1", "Chicago Bears", "Bears", "CHI", logo = "https://logo/chi.png"))

        SportsCache.writeTeams(leagueId, teams, 99L)
        val read = assertNotNull(SportsCache.readTeams(leagueId))

        assertEquals(99L, read.fetchedAtMs)
        assertEquals("https://logo/chi.png", read.teams.single().logo)
    }

    @Test
    fun `clearing games keeps the team lists`() {
        SportsCache.writeGames(leagueId, "20261117", "20261117", listOf(game("1")), 1L)
        SportsCache.writeTeams(leagueId, listOf(Team("1", "A", "A", "A")), 2L)

        SportsCache.clearGames()

        assertNull(SportsCache.readGames(leagueId, "20261117", "20261117"))
        assertNotNull(SportsCache.readTeams(leagueId), "team lists barely change and are expensive to refetch")
    }

    @Test
    fun `a league id containing a slash still produces a flat file name`() {
        // "soccer/eng.1" must not become a directory; the cache root stays one level deep.
        SportsCache.writeGames("soccer/eng.1", "20261117", "20261117", listOf(game("1")), 5L)

        assertNotNull(SportsCache.readGames("soccer/eng.1", "20261117", "20261117"))
        val root = java.nio.file.Path.of(System.getProperty("nuvio.desktop.dataDir"))
        val cacheRoot = Files.walk(root).use { stream ->
            stream.filter { it.fileName.toString() == "sports-cache" && Files.isDirectory(it) }
                .findFirst().orElse(null)
        }
        assertNotNull(cacheRoot, "the sports cache directory should exist")
        Files.list(cacheRoot).use { paths ->
            assertTrue(paths.allMatch { Files.isRegularFile(it) }, "cache files must not nest")
        }
    }
}
