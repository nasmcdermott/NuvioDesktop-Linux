package com.nuvio.app.features.sports

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The player-stat table's column choice: keep the reading columns, drop the noise. */
class SportsDetailExtrasTest {

    @Test
    fun `hockey drops the per-shift noise and keeps the scoring columns`() {
        val labels = listOf(
            "BS", "HT", "TK", "+/-", "TOI", "PPTOI", "SHTOI", "ESTOI", "SHFT",
            "G", "YTDG", "A", "S", "SM", "SOG", "FW", "FL", "FO%", "GV", "PN", "PIM",
        )

        val columns = sportsPlayerStatColumns(labels)

        assertEquals(5, columns.size)
        assertEquals(columns.sorted(), columns, "the provider's own left-to-right order is kept")
        assertTrue(columns.map { labels[it] }.containsAll(listOf("G", "A", "S", "TOI", "+/-")))
    }

    @Test
    fun `a short table keeps every column`() {
        assertEquals(listOf(0, 1, 2), sportsPlayerStatColumns(listOf("C/ATT", "YDS", "TD")))
    }

    @Test
    fun `unknown labels fall back to the first columns rather than showing none`() {
        val labels = listOf("FOO", "BAR", "BAZ", "QUX", "ZAP", "ZIP", "ZUM")

        assertEquals(listOf(0, 1, 2, 3, 4), sportsPlayerStatColumns(labels))
    }

    @Test
    fun `no labels means no columns`() {
        assertTrue(sportsPlayerStatColumns(emptyList()).isEmpty())
    }
}
