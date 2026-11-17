package com.nuvio.app.features.navbar

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class NavBarLayoutTest {

    /**
     * Navigation order lists an entry only when this build has it. Sports is the case that matters:
     * its row reached the dialog on builds without the feature, showing a row whose switch was
     * correctly absent — an entry nobody could show, hide or explain.
     */
    @Test
    fun theOrderDialogOnlyListsEntriesThisBuildCarries() {
        val listed = DEFAULT_NAV_BAR_ORDER.filter { it.existsInThisBuild() }

        if (com.nuvio.app.core.build.AppFeaturePolicy.sportsEnabled) {
            assertEquals(true, NavBarEntry.Sports in listed, "a Sports build should list Sports")
        } else {
            assertEquals(false, NavBarEntry.Sports in listed, "a build without the feature must not list Sports")
        }
        // Everything else is unconditional.
        assert(NavBarEntry.LiveTv.existsInThisBuild())
        assert(NavBarEntry.YouTube.existsInThisBuild())
    }

    @Test
    fun theDefaultPutsFilmsFirstAndMusicThenGamesLast() {
        assertEquals(DEFAULT_NAV_BAR_ORDER, parseNavBarOrder(null))
        assertEquals(
            listOf("home", "search", "tonight", "discover", "library", "livetv", "sports", "youtube", "music", "games"),
            parseNavBarOrder("").map { it.key },
        )
    }

    @Test
    fun aSavedOrderKeepsItsOrderAndGainsNewEntriesInTheirDefaultPlace() {
        // Saved before Games existed, with Music moved to the front: Games follows Music, the entry
        // it comes after by default, wherever Music now is.
        val parsed = parseNavBarOrder("music,home,search,discover,library,livetv,youtube,bogus")
        assertEquals(
            listOf("music", "games", "home", "search", "tonight", "discover", "library", "livetv", "sports", "youtube"),
            parsed.map { it.key },
        )
    }

    @Test
    fun slotsNarrowAsTheBarFillsButNotPastAFloor() {
        assertEquals(64.dp, navBarSlotWidth(4))
        assertEquals(64.dp, navBarSlotWidth(6))
        assertEquals(60.dp, navBarSlotWidth(7))
        assertEquals(52.dp, navBarSlotWidth(9))
        assertEquals(50.dp, navBarSlotWidth(12))
    }
}
