package com.nuvio.app.features.sports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.LazyListScope
import com.nuvio.app.features.settings.SettingsActionRow
import com.nuvio.app.features.settings.SettingsGlassButton
import com.nuvio.app.features.settings.SettingsGroup
import com.nuvio.app.features.settings.SettingsGroupDivider
import com.nuvio.app.features.settings.SettingsSectionNote
import com.nuvio.app.features.settings.SettingsSection
import com.nuvio.app.features.settings.SettingsSwitchRow

/**
 * Settings > Sports.
 *
 * Matches the Live TV page's shape — a General section of switches — because the two are read
 * together: Sports is idle unless a playlist exists, and the page says so rather than leaving the
 * user to discover it.
 */
internal fun LazyListScope.sportsSettingsContent(isTablet: Boolean) {
    item {
        val settings = rememberSportsSettings()
        SettingsSection(title = "Sports", isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = "Show Sports in navigation",
                    description = "Needs a playlist under Live TV. Turning this off hides the tab and keeps " +
                        "your channel picks.",
                    checked = settings.enabled,
                    isTablet = isTablet,
                    onCheckedChange = SportsSettingsRepository::setEnabled,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = "Hide scores",
                    description = "Hides scores and clocks, so a game you have not caught up with is not spoiled.",
                    checked = settings.hideScores,
                    isTablet = isTablet,
                    onCheckedChange = SportsSettingsRepository::setHideScores,
                )
                SettingsGroupDivider(isTablet = isTablet)
                // fork:sports - the follows live in the picker; this row is the way in and a summary.
                var pickerOpen by remember { mutableStateOf(false) }
                SettingsActionRow(
                    title = "Followed teams and leagues",
                    description = when (val followed = com.nuvio.app.features.sports.SportsFollows.count(settings)) {
                        0 -> "Nothing followed yet. Followed games lead the Sports page."
                        1 -> "1 followed."
                        else -> "$followed followed."
                    },
                    isTablet = isTablet,
                ) {
                    SettingsGlassButton(text = "Choose", onClick = { pickerOpen = true })
                }
                if (pickerOpen) {
                    com.nuvio.app.features.sports.SportsFollowPicker(onDismiss = { pickerOpen = false })
                }
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = "Clear channel picks",
                    // Reaching for this by accident would undo the work of matching every game by
                    // hand, so it asks first rather than acting on one press.
                    description = if (settings.picks.isEmpty()) {
                        "No channel picks yet. Games are matched from your guide and channel names."
                    } else {
                        "${settings.picks.size} picks. Forgets every channel you chose by hand and every one " +
                            "you marked as wrong."
                    },
                    checked = false,
                    isTablet = isTablet,
                    onCheckedChange = { on ->
                        if (on) SportsSettingsRepository.clearPicks()
                    },
                )
            }
        }
    }

    item {
        SettingsSection(title = "Where games come from", isTablet = isTablet) {
            SettingsSectionNote(
                text = "Games are found on the channels in your playlists, using the guide to see what " +
                    "is on at kick-off. Add or edit playlists under Live TV. Your follows and channel " +
                    "picks are part of a settings backup, per profile; fetched games are not, and " +
                    "rebuild themselves.",
                isTablet = isTablet,
            )
        }
    }
}

@Composable
private fun rememberSportsSettings(): SportsSettings {
    val state by SportsSettingsRepository.settings.collectAsState()
    return state
}
