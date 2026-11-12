package com.nuvio.app.features.player.desktop

import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinuxHudNavTest {
    private val select = KeyEvent.VK_ENTER
    private val playPause = KeyEvent.VK_SPACE

    private val dismiss = KeyEvent.VK_ESCAPE
    private fun route(keyCode: Int, state: Int) = linuxHudNavKey(keyCode, state, select, playPause, dismiss)

    @Test
    fun unavailableStateKeepsEveryKeysNormalMeaning() {
        for (key in listOf(KeyEvent.VK_LEFT, KeyEvent.VK_UP, select, playPause)) {
            assertNull(route(key, LinuxHudNavState.UNAVAILABLE), "key $key")
        }
    }

    @Test
    fun hiddenHudLeavesArrowsToSeekAndVolumeAndSelectRevealsIt() {
        assertNull(route(KeyEvent.VK_LEFT, LinuxHudNavState.HIDDEN))
        assertNull(route(KeyEvent.VK_DOWN, LinuxHudNavState.HIDDEN))
        assertEquals("Enter", route(select, LinuxHudNavState.HIDDEN))
        assertNull(route(playPause, LinuxHudNavState.HIDDEN))
    }

    @Test
    fun visibleHudDrivesArrowsAndSelectButNotPlayPause() {
        assertEquals("ArrowLeft", route(KeyEvent.VK_LEFT, LinuxHudNavState.VISIBLE))
        assertEquals("ArrowRight", route(KeyEvent.VK_RIGHT, LinuxHudNavState.VISIBLE))
        assertEquals("ArrowUp", route(KeyEvent.VK_UP, LinuxHudNavState.VISIBLE))
        assertEquals("ArrowDown", route(KeyEvent.VK_DOWN, LinuxHudNavState.VISIBLE))
        assertEquals("Enter", route(select, LinuxHudNavState.VISIBLE))
        assertNull(route(playPause, LinuxHudNavState.VISIBLE))
    }

    @Test
    fun focusedControlAlsoActivatesOnPlayPause() {
        assertEquals("ArrowLeft", route(KeyEvent.VK_LEFT, LinuxHudNavState.FOCUSED))
        assertEquals("Enter", route(select, LinuxHudNavState.FOCUSED))
        assertEquals("Enter", route(playPause, LinuxHudNavState.FOCUSED))
    }

    @Test
    fun theVolumeSliderTakesAllArrowsAndEscapeLeavesIt() {
        assertEquals("ArrowLeft", route(KeyEvent.VK_LEFT, LinuxHudNavState.VOLUME_FOCUSED))
        assertEquals("ArrowRight", route(KeyEvent.VK_RIGHT, LinuxHudNavState.VOLUME_FOCUSED))
        assertEquals("ArrowUp", route(KeyEvent.VK_UP, LinuxHudNavState.VOLUME_FOCUSED))
        assertEquals("ArrowDown", route(KeyEvent.VK_DOWN, LinuxHudNavState.VOLUME_FOCUSED))
        assertEquals("Escape", route(dismiss, LinuxHudNavState.VOLUME_FOCUSED))
        assertNull(route(dismiss, LinuxHudNavState.VISIBLE))
        assertNull(route(dismiss, LinuxHudNavState.FOCUSED))
    }

    @Test
    fun unrelatedKeysAreNeverRouted() {
        for (state in listOf(LinuxHudNavState.HIDDEN, LinuxHudNavState.VISIBLE, LinuxHudNavState.FOCUSED)) {
            assertNull(route(KeyEvent.VK_A, state))
            assertNull(route(KeyEvent.VK_ESCAPE, state))
        }
    }

    @Test
    fun reboundSelectAndPlayPauseFollowTheBindings() {
        val reboundSelect = KeyEvent.VK_SPACE
        val reboundPlayPause = KeyEvent.VK_K
        assertEquals("Enter", linuxHudNavKey(KeyEvent.VK_SPACE, LinuxHudNavState.VISIBLE, reboundSelect, reboundPlayPause, dismiss))
        assertEquals("Enter", linuxHudNavKey(KeyEvent.VK_K, LinuxHudNavState.FOCUSED, reboundSelect, reboundPlayPause, dismiss))
        assertNull(linuxHudNavKey(KeyEvent.VK_K, LinuxHudNavState.VISIBLE, reboundSelect, reboundPlayPause, dismiss))
        assertNull(linuxHudNavKey(KeyEvent.VK_ENTER, LinuxHudNavState.VISIBLE, reboundSelect, reboundPlayPause, dismiss))
    }
}

/** Up/Down routing with "Player volume control" on and off. Left/Right never reach volume. */
class LinuxVolumeKeyRoutingTest {
    private data class Row(val key: Int, val enabled: Boolean, val expected: String?)

    private val truthTable = listOf(
        Row(KeyEvent.VK_UP, enabled = true, expected = "volumeUp"),
        Row(KeyEvent.VK_DOWN, enabled = true, expected = "volumeDown"),
        Row(KeyEvent.VK_UP, enabled = false, expected = null),
        Row(KeyEvent.VK_DOWN, enabled = false, expected = null),
        Row(KeyEvent.VK_LEFT, enabled = true, expected = null),
        Row(KeyEvent.VK_RIGHT, enabled = true, expected = null),
        Row(KeyEvent.VK_LEFT, enabled = false, expected = null),
        Row(KeyEvent.VK_RIGHT, enabled = false, expected = null),
    )

    @Test
    fun routingTruthTable() {
        for (row in truthTable) {
            assertEquals(row.expected, linuxVolumeKeyType(row.key, row.enabled), "key ${row.key} enabled ${row.enabled}")
        }
    }
}

class LinuxHudSelectRepeatGuardTest {
    @Test
    fun aHeldKeyIsAcceptedOnceUntilReleased() {
        val guard = LinuxHudSelectRepeatGuard()
        assertEquals(true, guard.accept(KeyEvent.VK_ENTER))
        assertEquals(false, guard.accept(KeyEvent.VK_ENTER))
        assertEquals(false, guard.accept(KeyEvent.VK_ENTER))
        guard.release(KeyEvent.VK_ENTER)
        assertEquals(true, guard.accept(KeyEvent.VK_ENTER))
    }

    @Test
    fun keysAreTrackedIndependently() {
        val guard = LinuxHudSelectRepeatGuard()
        assertEquals(true, guard.accept(KeyEvent.VK_ENTER))
        assertEquals(true, guard.accept(KeyEvent.VK_SPACE))
        guard.release(KeyEvent.VK_SPACE)
        assertEquals(false, guard.accept(KeyEvent.VK_ENTER))
    }
}

class PlayerOwnsKeysTest {
    @Test
    fun ownsKeysOnlyWhenTheVideoFillsTheWindow() {
        assertEquals(true, playerOwnsKeys(miniPlayer = false, pictureInPicture = false, browsePip = false))
        assertEquals(false, playerOwnsKeys(miniPlayer = true, pictureInPicture = false, browsePip = false))
        assertEquals(false, playerOwnsKeys(miniPlayer = false, pictureInPicture = true, browsePip = false))
        assertEquals(false, playerOwnsKeys(miniPlayer = false, pictureInPicture = false, browsePip = true))
        assertEquals(false, playerOwnsKeys(miniPlayer = true, pictureInPicture = true, browsePip = true))
    }
}

class YouTubeWatchFocusAfterTest {
    @Test
    fun downFromTheHudBottomRowGoesToThePage() {
        assertEquals(
            YouTubeWatchFocusLocation.PageFirstControl,
            youTubeWatchFocusAfter(YouTubeWatchFocusLocation.HudBottomRow, KeyEvent.VK_DOWN),
        )
    }

    @Test
    fun upFromThePageFirstControlReturnsToTheHud() {
        assertEquals(
            YouTubeWatchFocusLocation.HudBottomRow,
            youTubeWatchFocusAfter(YouTubeWatchFocusLocation.PageFirstControl, KeyEvent.VK_UP),
        )
    }

    @Test
    fun everyOtherPressHasNoCrossBoundaryMeaning() {
        assertNull(youTubeWatchFocusAfter(YouTubeWatchFocusLocation.HudBottomRow, KeyEvent.VK_UP))
        assertNull(youTubeWatchFocusAfter(YouTubeWatchFocusLocation.HudBottomRow, KeyEvent.VK_LEFT))
        assertNull(youTubeWatchFocusAfter(YouTubeWatchFocusLocation.PageFirstControl, KeyEvent.VK_DOWN))
        assertNull(youTubeWatchFocusAfter(YouTubeWatchFocusLocation.PageFirstControl, KeyEvent.VK_RIGHT))
        assertNull(youTubeWatchFocusAfter(YouTubeWatchFocusLocation.PageFirstControl, KeyEvent.VK_ENTER))
    }
}
