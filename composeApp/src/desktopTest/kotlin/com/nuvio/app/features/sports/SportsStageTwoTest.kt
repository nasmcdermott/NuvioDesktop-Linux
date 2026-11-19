package com.nuvio.app.features.sports

import com.nuvio.app.features.navbar.DEFAULT_NAV_BAR_ORDER
import com.nuvio.app.features.navbar.NavBarEntry
import com.nuvio.app.features.navbar.parseNavBarOrder
import java.time.LocalDate
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The pieces Stage 2 adds that can be checked without a screen: the date strip's days, the settings
 * defaults, and the navigation order's treatment of a newly added entry.
 */
class SportsStageTwoTest {

    @Test
    fun `moneyline colors split in proportion to normalized implied chances`() {
        // +120 implies 45.45%, -140 implies 58.33%; normalize their sum to remove the vig.
        val share = moneylineAwayShare(MoneylineOdds(away = "+120", home = "-140"))

        assertEquals(45.45f / (45.45f + 58.33f), share, 0.001f)
    }

    @Test
    fun `moneyline color split handles one quote and missing or invalid quotes`() {
        assertEquals(100f / 220f, moneylineAwayShare(MoneylineOdds(away = "+120")), 0.001f)
        assertEquals(1f - 140f / 240f, moneylineAwayShare(MoneylineOdds(home = "-140")), 0.001f)
        assertEquals(0.5f, moneylineAwayShare(null))
        assertEquals(0.5f, moneylineAwayShare(MoneylineOdds(away = "EVEN", home = "—")))
    }

    @Test
    fun `the strip is a full week centred on today`() {
        val today = LocalDate.of(2026, 11, 17)
        val days = sportsDays(today, radius = 3, locale = Locale.US)

        assertEquals(7, days.size)
        assertEquals("20261114", days.first().day)
        assertEquals("20261120", days.last().day)
        assertEquals("20261117", days[3].day)
    }

    @Test
    fun `yesterday, today and tomorrow are named, the rest are dated`() {
        val days = sportsDays(LocalDate.of(2026, 11, 17), radius = 3, locale = Locale.US)

        assertEquals("Yesterday", days[2].label)
        assertEquals("Today", days[3].label)
        assertEquals("Tomorrow", days[4].label)
        // The others carry the date, so the strip still reads without knowing the weekday order.
        assertEquals("Nov 15", days[1].label)
        assertEquals("Nov 19", days[5].label)
    }

    @Test
    fun `the weekday follows the given locale`() {
        val today = LocalDate.of(2026, 11, 17) // a Tuesday
        assertEquals("Tue", sportsDays(today, locale = Locale.US)[3].weekday)
        assertEquals("Di.", sportsDays(today, locale = Locale.GERMANY)[3].weekday) // German abbreviates with a full stop
    }

    @Test
    fun `days cross a month and a year boundary correctly`() {
        val days = sportsDays(LocalDate.of(2026, 12, 31), radius = 1, locale = Locale.US)

        assertEquals(listOf("20261230", "20261231", "20270101"), days.map { it.day })
    }

    @Test
    fun `settings default to on, with scores shown and nothing hidden`() {
        val settings = SportsSettings()

        assertTrue(settings.enabled)
        assertTrue(!settings.hideScores)
        assertTrue(settings.hiddenLeagues.isEmpty())
        assertTrue(settings.picks.isEmpty())
        assertTrue(settings.blocked.isEmpty())
    }

    @Test
    fun `the sports entry sits after Live TV and before YouTube by default`() {
        val liveTv = DEFAULT_NAV_BAR_ORDER.indexOf(NavBarEntry.LiveTv)
        val sports = DEFAULT_NAV_BAR_ORDER.indexOf(NavBarEntry.Sports)
        val youTube = DEFAULT_NAV_BAR_ORDER.indexOf(NavBarEntry.YouTube)

        assertTrue(sports > liveTv, "Sports should follow Live TV")
        assertTrue(sports < youTube, "Sports should precede YouTube")
    }

    @Test
    fun `a navigation order saved before Sports existed puts it back after Live TV`() {
        // The order a user saved on a build that had no Sports entry at all.
        val saved = "home,search,tonight,discover,library,livetv,youtube,music,games"

        val parsed = parseNavBarOrder(saved)

        assertTrue(NavBarEntry.Sports in parsed, "an unknown entry must not be dropped")
        val index = parsed.indexOf(NavBarEntry.Sports)
        assertEquals(NavBarEntry.LiveTv, parsed[index - 1])
        assertEquals(NavBarEntry.YouTube, parsed[index + 1])
    }

    @Test
    fun `the settings store name is the one the backup looks for`() {
        assertEquals("nuvio_sports", SPORTS_STORE_NAME)
    }
}
