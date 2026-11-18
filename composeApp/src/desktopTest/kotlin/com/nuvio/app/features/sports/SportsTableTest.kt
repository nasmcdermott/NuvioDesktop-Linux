package com.nuvio.app.features.sports

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SportsTableTest {

    private fun row(vararg pairs: Pair<String, String>, name: String = "Team") = Standing(
        team = Team(id = name, name = name, shortName = name, abbreviation = name.take(3)),
        values = pairs.map { StandingValue(it.first, it.second) },
    )

    @Test
    fun `the preferred columns come first and in their own order`() {
        val rows = listOf(
            row("DIFF" to "+88", "PCT" to ".889", "W" to "8", "L" to "1", "PF" to "287"),
        )

        assertEquals(
            listOf("W", "L", "PCT", "DIFF", "PF"),
            SportsTable.columns(rows).map { it.label },
        )
    }

    @Test
    fun `a league whose headings are all unfamiliar still gets a table`() {
        val rows = listOf(row("SOMETHING" to "1", "ELSE" to "2"))

        assertEquals(listOf("ELSE", "SOMETHING"), SportsTable.columns(rows).map { it.label })
    }

    @Test
    fun `columns are capped so the table stays one screen wide`() {
        val rows = listOf(
            row("W" to "1", "L" to "2", "T" to "3", "OTL" to "4", "PCT" to "5", "GP" to "6", "PF" to "7", "PA" to "8"),
        )

        assertEquals(SportsTable.MAX_COLUMNS, SportsTable.columns(rows).size)
    }

    @Test
    fun `a column nobody has a value for is dropped`() {
        val rows = listOf(row("W" to "8", "L" to "1"))

        assertTrue(SportsTable.columns(rows).none { it.label == "PCT" })
    }

    @Test
    fun `a row narrower than the columns is padded, not misaligned`() {
        val rows = listOf(row("W" to "8"), row("W" to "9", "L" to "2"))
        val columns = SportsTable.columns(rows)

        val cells = SportsTable.cells(rows.first(), columns)

        assertEquals(columns.size, cells.size)
        assertEquals(listOf("8", "—"), cells)
    }

    @Test
    fun `a blank value reads as missing rather than as an empty cell`() {
        val row = row("P" to "27", "PCT" to "")

        val cells = SportsTable.cells(row, listOf(SportsTable.Column("P"), SportsTable.Column("PCT")))

        assertEquals(listOf("27", SportsTable.MISSING), cells)
    }

    @Test
    fun `soccer tables pick P W D L GD PTS in standard order`() {
        val epl = SportsCatalog.league("soccer/eng.1")!!
        val arsenal = Standing(
            team = Team("359", "Arsenal", "Arsenal", "ARS"),
            rank = 1,
            note = "Champions League",
            values = listOf(
                StandingValue("P", "11", "gamesPlayed"),
                StandingValue("W", "8", "wins"),
                StandingValue("D", "2", "ties"),
                StandingValue("L", "1", "losses"),
                StandingValue("GD", "+15", "pointDifferential"),
                StandingValue("PTS", "26", "points"),
            ),
            sortValue = 26_015.0,
        )

        val table = SportsTable.build(epl, listOf(arsenal))

        assertEquals(SportsTable.Kind.TABLE, table.kind)
        assertEquals(listOf("P", "W", "D", "L", "GD", "PTS"), table.columns.map { it.label })
        assertEquals("1st in Premier League", table.placeText("359"))
        assertEquals(1, table.zones.size)
        assertEquals(0xFF2B7DE9L, table.zones.single().color)
    }

    @Test
    fun `divisions merge into conference by seed and into league by sortValue`() {
        val nfl = SportsCatalog.league("football/nfl")!!
        val bills = Standing(
            team = Team("2", "Buffalo Bills", "Bills", "BUF"),
            rank = 1,
            group = "AFC East",
            conference = "American Football Conference",
            seed = 2,
            sortValue = 889.0,
            values = listOf(StandingValue("W", "8", "wins"), StandingValue("L", "1", "losses"), StandingValue("PCT", ".889", "winPercent")),
        )
        val chiefs = Standing(
            team = Team("12", "Kansas City Chiefs", "Chiefs", "KC"),
            rank = 1,
            group = "AFC West",
            conference = "American Football Conference",
            seed = 1,
            sortValue = 900.0,
            values = listOf(StandingValue("W", "9", "wins"), StandingValue("L", "0", "losses"), StandingValue("PCT", "1.000", "winPercent")),
        )
        val lions = Standing(
            team = Team("8", "Detroit Lions", "Lions", "DET"),
            rank = 1,
            group = "NFC North",
            conference = "National Football Conference",
            seed = 1,
            sortValue = 895.0,
            values = listOf(StandingValue("W", "8", "wins"), StandingValue("L", "1", "losses"), StandingValue("PCT", ".889", "winPercent")),
        )

        val table = SportsTable.build(nfl, listOf(bills, chiefs, lions))

        assertTrue(table.hasDivisions)
        assertEquals(listOf("American Football Conference", "National Football Conference"), table.conferences)

        val afcDivs = table.groupsFor(SportsTable.View.DIVISION, "American Football Conference")
        assertEquals(2, afcDivs.size)

        val afcConf = table.groupsFor(SportsTable.View.CONFERENCE, "American Football Conference").single()
        assertEquals(listOf("Chiefs", "Bills"), afcConf.rows.map { it.team.shortName })
        assertEquals(listOf(1, 2), afcConf.rows.map { it.rank })

        val fullLeague = table.groupsFor(SportsTable.View.LEAGUE, null).single()
        assertEquals(listOf("Chiefs", "Lions", "Bills"), fullLeague.rows.map { it.team.shortName })
        assertEquals(listOf(7), table.playoffLines(SportsTable.View.CONFERENCE).map { it.afterRank })
    }

    @Test
    fun `recent finished games produce oldest-first form string`() {
        val games = listOf(
            SportsEvent(
                id = "1", leagueId = "soccer/eng.1", startMs = 100L, state = GameState.POST,
                away = GameSide(Team("1", "Arsenal", "Arsenal", "ARS"), score = "2"),
                home = GameSide(Team("2", "Chelsea", "Chelsea", "CHE"), score = "1"),
            ),
            SportsEvent(
                id = "2", leagueId = "soccer/eng.1", startMs = 200L, state = GameState.POST,
                away = GameSide(Team("3", "Liverpool", "Liverpool", "LIV"), score = "2"),
                home = GameSide(Team("1", "Arsenal", "Arsenal", "ARS"), score = "2"),
            ),
            SportsEvent(
                id = "3", leagueId = "soccer/eng.1", startMs = 300L, state = GameState.POST,
                away = GameSide(Team("1", "Arsenal", "Arsenal", "ARS"), score = "0"),
                home = GameSide(Team("4", "Newcastle", "Newcastle", "NEW"), score = "1"),
            ),
        )

        val form = SportsTable.formFrom(games, "soccer/eng.1")

        assertEquals("WDL", form["1"])
    }
}
