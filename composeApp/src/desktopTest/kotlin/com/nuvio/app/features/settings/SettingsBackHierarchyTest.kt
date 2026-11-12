package com.nuvio.app.features.settings

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.AppTheme
import com.nuvio.app.core.ui.DesktopNavigationGestureBridge
import com.nuvio.app.core.ui.NuvioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Back hierarchy from a settings item's perspective: a content control on a top-level page
 * returns to the sidebar, a nested page keeps Back on the bridge so it goes to its parent, and the
 * sidebar root keeps Back on the bridge so it exits Settings.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsBackHierarchyTest {

    @Test
    fun backFromTopLevelContentReturnsToTheSidebar() = runComposeUiTest {
        var backRequests = 0
        GlobalScope.launch(Dispatchers.Unconfined) {
            DesktopNavigationGestureBridge.backRequests.collect { backRequests += 1 }
        }
        var sidebarFocused = false
        val row = FocusRequester()
        val sidebarEntry = FocusRequester()
        setContent {
            NuvioTheme(darkTheme = true, appTheme = AppTheme.CRIMSON) {
                Column {
                    CompositionLocalProvider(
                        LocalSettingsFocusRegion provides SettingsFocusRegion.Content,
                        LocalSettingsPage provides SettingsPage.WatchParty,
                    ) {
                        Box(
                            Modifier
                                .size(60.dp)
                                .focusRequester(row)
                                .settingsNavItem(onActivate = {})
                                .focusable(),
                        )
                    }
                    SidebarEntryBox(
                        requester = sidebarEntry,
                        onFocused = { sidebarFocused = it },
                    )
                }
            }
        }
        waitForIdle()
        runOnIdle { row.requestFocus() }
        waitForIdle()

        onRoot().performKeyInput { pressKey(Key.Backspace) }
        waitForIdle()
        assertTrue(sidebarFocused, "Back from a top-level page's content returns to the sidebar")
        assertEquals(0, backRequests, "and must not exit Settings")
    }

    @Test
    fun backFromNestedContentGoesToTheParent() = runComposeUiTest {
        var backRequests = 0
        GlobalScope.launch(Dispatchers.Unconfined) {
            DesktopNavigationGestureBridge.backRequests.collect { backRequests += 1 }
        }
        var sidebarFocused = false
        val row = FocusRequester()
        val sidebarEntry = FocusRequester()
        setContent {
            NuvioTheme(darkTheme = true, appTheme = AppTheme.CRIMSON) {
                Column {
                    CompositionLocalProvider(
                        LocalSettingsFocusRegion provides SettingsFocusRegion.Content,
                        LocalSettingsPage provides SettingsPage.StreamScoring,
                    ) {
                        Box(
                            Modifier
                                .size(60.dp)
                                .focusRequester(row)
                                .settingsNavItem(onActivate = {})
                                .focusable(),
                        )
                    }
                    SidebarEntryBox(
                        requester = sidebarEntry,
                        onFocused = { sidebarFocused = it },
                    )
                }
            }
        }
        waitForIdle()
        runOnIdle { row.requestFocus() }
        waitForIdle()

        onRoot().performKeyInput { pressKey(Key.Backspace) }
        waitForIdle()
        assertEquals(1, backRequests, "Back from a nested page stays on the bridge (to its parent)")
        assertEquals(false, sidebarFocused, "and does not jump straight to the sidebar")
    }

    @Test
    fun backFromTheSidebarRootExitsSettings() = runComposeUiTest {
        var backRequests = 0
        GlobalScope.launch(Dispatchers.Unconfined) {
            DesktopNavigationGestureBridge.backRequests.collect { backRequests += 1 }
        }
        val row = FocusRequester()
        setContent {
            NuvioTheme(darkTheme = true, appTheme = AppTheme.CRIMSON) {
                CompositionLocalProvider(LocalSettingsFocusRegion provides SettingsFocusRegion.Sidebar) {
                    Box(
                        Modifier
                            .size(60.dp)
                            .focusRequester(row)
                            .settingsNavItem(onActivate = {})
                            .focusable(),
                    )
                }
            }
        }
        waitForIdle()
        runOnIdle { row.requestFocus() }
        waitForIdle()

        onRoot().performKeyInput { pressKey(Key.Backspace) }
        waitForIdle()
        assertEquals(1, backRequests, "Back from the sidebar root stays on the bridge (to exit Settings)")
    }

    /** A focusable that also serves as the registered sidebar entry, so Back's destination is observable. */
    @Composable
    private fun SidebarEntryBox(requester: FocusRequester, onFocused: (Boolean) -> Unit) {
        val focusManager = LocalFocusManager.current
        DisposableEffect(requester, focusManager) {
            SettingsSidebarFocus.register(requester, focusManager)
            onDispose { SettingsSidebarFocus.unregister(requester) }
        }
        Box(
            Modifier
                .size(60.dp)
                .focusRequester(requester)
                .onFocusChanged { onFocused(it.hasFocus) }
                .focusable(),
        )
    }
}
