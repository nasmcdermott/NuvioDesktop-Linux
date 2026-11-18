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
        // The feed does not sort its stats; Buffalo's arrive with differential first. A table wants
        // wins before percentages, whatever order the provider chose.
        // Labels as the feed actually delivers them: StatRow stores the abbreviation ("DIFF"),
        // never the camelCase API name.
        val rows = listOf(
            row("DIFF" to "+88", "PCT" to ".889", "W" to "8", "L" to "1", "PF" to "287"),
        )

        // PREFERRED order: record, then rate, then volume. DIFF is a rate and sits ahead of PF.
        assertEquals(
            listOf("W", "L", "PCT", "DIFF", "PF"),
            SportsTable.columns(rows).map { it.label },
        )
    }

    @Test
    fun `a league whose headings are all unfamiliar still gets a table`() {
        // Never fall back to showing nothing just because a league names its columns oddly.
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
        // A stripe of dashes is worse than no column: it suggests data that is not there.
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
        // Soccer sends "deductions" as "", which is "none", not "zero": a dash says so.
        val row = row("P" to "27", "PCT" to "")

        val cells = SportsTable.cells(row, listOf(SportsTable.Column("P"), SportsTable.Column("PCT")))

        assertEquals(listOf("27", SportsTable.MISSING), cells)
    }
}
