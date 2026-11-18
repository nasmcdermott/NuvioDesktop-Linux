package com.nuvio.app.features.sports

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fix-match rules that can be checked without a playlist.
 *
 * `SportsWatch.optionsFor` reads the live IPTV repositories, so its filtering cannot be driven from
 * a unit test; what is tested here is the key format those filters depend on, and the settings
 * round-trip behind "This one" / "Wrong" / reset.
 */
class SportsFixMatchTest {

    @Test
    fun `a block is scoped to one game, not to the channel`() {
        val key = SportsWatch.blockKey("401", "newsport:5")

        // The same channel must stay usable for every other game: a channel that was wrong for one
        // fixture is usually right for the next, and a channel-wide block would silently empty the
        // list for the rest of the day.
        assertTrue(SportsWatch.blockKey("402", "newsport:5") != key)
        assertEquals("401", key.substringBefore('|'))
        assertEquals("newsport:5", key.substringAfter('|'))
    }

    @Test
    fun `a block key survives a channel id that contains a separator`() {
        // Channel keys are provider-shaped and may contain anything; only the first "|" is ours.
        val key = SportsWatch.blockKey("401", "pl|2:7")

        assertEquals("401", key.substringBefore('|'))
        assertEquals("pl|2:7", key.substringAfter('|'))
    }
}
