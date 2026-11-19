package com.nuvio.app.features.sports

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The quarter toggles ("Latest 5" / "Show less") sit in one tall plays card, so Down and Up belong
 * to scrolling the page. Left and Right are what walk from one quarter's toggle to the next; at
 * either end they keep their usual meaning instead of pinning focus.
 */
class SportsPlayByPlayFocusTest {

    @Test
    fun `left and right move between the quarters' toggles`() {
        assertEquals(0, periodSideStepTarget(Key.DirectionLeft, index = 1, lastIndex = 3))
        assertEquals(2, periodSideStepTarget(Key.DirectionRight, index = 1, lastIndex = 3))
    }

    @Test
    fun `left at the first toggle and right at the last keep their usual meaning`() {
        assertNull(periodSideStepTarget(Key.DirectionLeft, index = 0, lastIndex = 3))
        assertNull(periodSideStepTarget(Key.DirectionRight, index = 3, lastIndex = 3))
    }

    @Test
    fun `only sideways keys step, and a card with no quarters steps nowhere`() {
        assertNull(periodSideStepTarget(Key.DirectionDown, index = 1, lastIndex = 3))
        assertNull(periodSideStepTarget(Key.DirectionUp, index = 1, lastIndex = 3))
        assertNull(periodSideStepTarget(Key.DirectionRight, index = 0, lastIndex = -1))
    }

    @Test
    fun `periods read newest first, with overtime above regulation and Other last`() {
        val periods = listOf("1st Quarter", "4th Quarter", "OT", "3rd Quarter", "2nd Quarter", "Other")

        val ordered = periods.sortedByDescending(::sportsPeriodOrder)

        assertEquals(
            listOf("OT", "4th Quarter", "3rd Quarter", "2nd Quarter", "1st Quarter", "Other"),
            ordered,
        )
        assertTrue(sportsPeriodOrder("2nd Half") > sportsPeriodOrder("1st Half"))
    }
}
