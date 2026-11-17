package com.nuvio.app.features.sports

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The league table is data, so what is worth testing is that the data is coherent: every league has
 * a usable provider path and something to look for in a channel name, ids are unique, and region
 * filtering never empties the page.
 */
class SportsCatalogTest {

    @Test
    fun `every league has a unique id`() {
        val ids = SportsCatalog.leagues.map { it.id }
        assertEquals(ids.size, ids.distinct().size, "duplicate league id: ${ids.groupBy { it }.filter { it.value.size > 1 }}")
    }

    @Test
    fun `every league has a provider path, a name and a usable duration`() {
        SportsCatalog.leagues.forEach { league ->
            assertTrue(league.path.isNotBlank(), "${league.id} has no path")
            assertTrue(league.name.isNotBlank(), "${league.id} has no name")
            assertTrue(league.shortName.isNotBlank(), "${league.id} has no short name")
            assertTrue(league.minutes in 30..600, "${league.id} has an implausible duration: ${league.minutes}")
        }
    }

    @Test
    fun `every league has at least one guide word, since a channel name is all that is left otherwise`() {
        SportsCatalog.leagues.forEach { league ->
            assertTrue(
                SportsCatalog.searchWords(league).isNotEmpty(),
                "${league.id} has nothing to search channel names for",
            )
        }
    }

    @Test
    fun `every league is offered in at least one region`() {
        SportsCatalog.leagues.forEach { league ->
            assertTrue(league.regions.isNotEmpty(), "${league.id} is offered nowhere")
            assertTrue(
                league.regions.all { it == "US" || it == "CA" || it == "UK" || it == "IE" || it == "OTHER" },
                "${league.id} names an unknown region: ${league.regions}",
            )
        }
    }

    @Test
    fun `league ids survive a round trip through the catalog`() {
        SportsCatalog.leagues.forEach { league ->
            assertEquals(league, SportsCatalog.league(league.id), "${league.id} does not resolve to itself")
        }
        assertNull(SportsCatalog.league("not/a/league"))
    }

    @Test
    fun `an unknown region offers everything rather than nothing`() {
        assertEquals(SportsCatalog.leagues, SportsCatalog.leaguesIn("ZZ"))
        assertEquals(SportsCatalog.leagues, SportsCatalog.leaguesIn(null))
        assertEquals(SportsCatalog.leagues, SportsCatalog.leaguesIn("  "))
    }

    @Test
    fun `a known region narrows the list but never empties it`() {
        val us = SportsCatalog.leaguesIn("us").also { list ->
            assertTrue(list.isNotEmpty())
            assertTrue(list.size < SportsCatalog.leagues.size, "US should not be every league")
        }
        assertTrue(us.all { "US" in it.regions })
        // Case and padding must not matter: the region arrives from a setting, not a constant.
        assertEquals(us, SportsCatalog.leaguesIn("  US  "))
    }

    @Test
    fun `sportsOf lists only sports that the given leagues cover, in enum order`() {
        val epl = assertNotNull(SportsCatalog.league("soccer/eng.1"))
        val nhl = assertNotNull(SportsCatalog.league("hockey/nhl"))

        val sports = SportsCatalog.sportsOf(listOf(epl, nhl))

        assertEquals(listOf(Sport.HOCKEY, Sport.FOOTBALL), sports)
    }

    @Test
    fun `search words are lowercased and de-duplicated`() {
        val nba = assertNotNull(SportsCatalog.league("basketball/nba"))

        val words = SportsCatalog.searchWords(nba)

        assertEquals(listOf("nba"), words)
        assertEquals(words.distinct(), words)
        assertTrue(words.all { it == it.lowercase() })
    }

    @Test
    fun `the leagues a v1 promises are all present`() {
        // Named so a catalog edit that drops one is a test failure, not a quiet disappearance.
        listOf(
            "football/nfl", "football/college-football",
            "basketball/nba", "basketball/wnba",
            "hockey/nhl", "baseball/mlb",
            "soccer/eng.1", "soccer/uefa.champions",
            "racing/f1", "golf/pga", "tennis/atp", "mma/ufc",
        ).forEach { id ->
            assertNotNull(SportsCatalog.league(id), "expected league $id in the catalog")
        }
    }
}
