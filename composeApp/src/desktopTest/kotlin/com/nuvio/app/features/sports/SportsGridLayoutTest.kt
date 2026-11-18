package com.nuvio.app.features.sports

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SportsGridLayoutTest {

    @Test
    fun `empty sections produce empty layout`() {
        val layout = SportsSectionGridLayout.build<String>(
            sections = listOf(
                SportsSectionGridLayout.Companion.SectionInput(
                    key = "live",
                    title = "LIVE NOW",
                    items = emptyList(),
                    live = true,
                    itemKey = { it },
                ),
            ),
            columns = 3,
        )
        assertTrue(layout.isEmpty)
        assertTrue(layout.entries.isEmpty())
        assertTrue(layout.rows.isEmpty())
    }

    @Test
    fun `wraps sections into multi-row grid and maps row-col indices across headers`() {
        val layout = SportsSectionGridLayout.build(
            sections = listOf(
                SportsSectionGridLayout.Companion.SectionInput(
                    key = "live",
                    title = "LIVE NOW",
                    items = listOf("L1", "L2", "L3", "L4", "L5"),
                    live = true,
                    itemKey = { it },
                ),
                SportsSectionGridLayout.Companion.SectionInput(
                    key = "empty",
                    title = "SKIPPED",
                    items = emptyList(),
                    itemKey = { it },
                ),
                SportsSectionGridLayout.Companion.SectionInput(
                    key = "nba",
                    title = "NBA · Coming up",
                    items = listOf("U1", "U2", "U3", "U4"),
                    accentColor = 0xFFC9082AL,
                    itemKey = { it },
                ),
            ),
            columns = 3,
        )

        assertFalse(layout.isEmpty)
        // Row 0: L1, L2, L3 (after header 0)
        // Row 1: L4, L5 (partial row at end of LIVE section)
        // Row 2: U1, U2, U3 (after header 6)
        // Row 3: U4 (partial row at end of NBA section)
        assertEquals(4, layout.rows.size)
        assertEquals(listOf(3, 2, 3, 1), layout.rows.map { it.size })

        // Verify every row[r][c] points to a Card entry with matching (r, c) coordinates.
        layout.rows.forEachIndexed { r, rowIndices ->
            rowIndices.forEachIndexed { c, entryIdx ->
                val card = layout.entries[entryIdx] as SportsSectionGridLayout.Entry.Card
                assertEquals(entryIdx, card.entryIndex)
                assertEquals(r, card.row)
                assertEquals(c, card.col)
            }
        }

        // Navigating Down from column 2 of row 0 (L3) clamps cleanly to column 1 of row 1 (L5).
        val clampedColInRow1 = 2.coerceAtMost(layout.rows[1].lastIndex)
        val targetCard = layout.entries[layout.rows[1][clampedColInRow1]] as SportsSectionGridLayout.Entry.Card
        assertEquals("L5", targetCard.item)

        // Navigating Down from row 1 (L5, col 1) enters NBA row 2 at col 1 (U2), crossing the section header cleanly.
        val nextCard = layout.entries[layout.rows[2][clampedColInRow1]] as SportsSectionGridLayout.Entry.Card
        assertEquals("U2", nextCard.item)
    }
}
