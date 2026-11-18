package com.nuvio.app.features.sports

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nuvio.app.features.iptv.IptvRepository
import com.nuvio.app.features.settings.SettingsActionRow
import com.nuvio.app.features.settings.SettingsGlassButton
import com.nuvio.app.features.settings.SettingsGroup
import com.nuvio.app.features.settings.SettingsGroupDivider
import com.nuvio.app.features.settings.SettingsSection
import com.nuvio.app.features.settings.SettingsSectionNote
import com.nuvio.app.features.settings.SettingsSwitchRow

/**
 * Settings > Sports.
 *
 * Matches the Live TV page's shape — a General section of switches and IPTV playback preferences —
 * because the two are read together: Sports hooks into the user's IPTV playlists and EPG guides.
 */
internal fun LazyListScope.sportsSettingsContent(isTablet: Boolean) {
    item {
        val settings = rememberSportsSettings()
        remember { IptvRepository.ensureLoaded() }
        val iptvSettings by IptvRepository.settings.collectAsState()
        val playlists = iptvSettings.playlists

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
                    description = "Hides scores, clocks and league tables, so a game you have not caught up with " +
                        "is not spoiled.",
                    checked = settings.hideScores,
                    isTablet = isTablet,
                    onCheckedChange = SportsSettingsRepository::setHideScores,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = "Only games on my channels",
                    description = "Hides games with no IPTV channel match, except games involving teams you follow.",
                    checked = settings.onlyGamesOnMyChannels,
                    isTablet = isTablet,
                    onCheckedChange = SportsSettingsRepository::setOnlyGamesOnMyChannels,
                )
                SettingsGroupDivider(isTablet = isTablet)
                var pickerOpen by remember { mutableStateOf(false) }
                SettingsActionRow(
                    title = "Followed teams and leagues",
                    description = when (val followed = SportsFollows.count(settings)) {
                        0 -> "Nothing followed yet. Followed games lead the Sports page."
                        1 -> "1 followed."
                        else -> "$followed followed."
                    },
                    isTablet = isTablet,
                ) {
                    SettingsGlassButton(text = "Choose", onClick = { pickerOpen = true })
                }
                if (pickerOpen) {
                    SportsFollowPicker(onDismiss = { pickerOpen = false })
                }
            }
        }

        SettingsSection(title = "IPTV playback & matching", isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                val activePlaylist = playlists.firstOrNull { it.id == settings.preferredPlaylistId }
                val serviceLabel = when {
                    playlists.isEmpty() -> "No playlists added"
                    activePlaylist != null -> activePlaylist.name.ifBlank { "Playlist" }
                    playlists.size == 1 -> "${playlists.first().name.ifBlank { "Default playlist" }} (Default)"
                    else -> "All playlists (${playlists.first().name.ifBlank { "First" }} leads)"
                }
                SettingsActionRow(
                    title = "Default IPTV service",
                    description = when {
                        playlists.isEmpty() -> "Add an M3U, Xtream or Stalker playlist in Live TV settings to play live games."
                        activePlaylist != null -> "Channels from ${activePlaylist.name.ifBlank { "this service" }} are matched and played first."
                        else -> "Searches all your IPTV playlists, starting with your primary Live TV playlist."
                    },
                    isTablet = isTablet,
                ) {
                    SettingsGlassButton(
                        text = serviceLabel,
                        onClick = {
                            if (playlists.size > 1) {
                                val ids = listOf("") + playlists.map { it.id }
                                val currentIdx = ids.indexOf(settings.preferredPlaylistId).coerceAtLeast(0)
                                val nextId = ids[(currentIdx + 1) % ids.size]
                                SportsSettingsRepository.setPreferredPlaylistId(nextId)
                            } else if (settings.preferredPlaylistId.isNotEmpty()) {
                                SportsSettingsRepository.setPreferredPlaylistId("")
                            }
                        },
                    )
                }
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = "Prefer highest stream quality",
                    description = "Ranks 4K and FHD feeds ahead of SD copies, and uses duplicate feeds as automatic " +
                        "failover backups during playback.",
                    checked = settings.bestQualityFirst,
                    isTablet = isTablet,
                    onCheckedChange = SportsSettingsRepository::setBestQualityFirst,
                )
                SettingsGroupDivider(isTablet = isTablet)
                val pickCount = settings.totalPickCount
                SettingsActionRow(
                    title = "Channel picks & exclusions",
                    description = when {
                        pickCount == 0 && settings.blocked.isEmpty() ->
                            "No manual overrides yet. Games are matched automatically from your EPG guides and channel names."
                        else ->
                            "$pickCount saved channel ${if (pickCount == 1) "pick" else "picks"} and " +
                                "${settings.blocked.size} hidden wrong ${if (settings.blocked.size == 1) "match" else "matches"}."
                    },
                    isTablet = isTablet,
                ) {
                    SettingsGlassButton(
                        text = "Clear all",
                        onClick = { SportsSettingsRepository.clearPicks() },
                    )
                }
            }
        }
    }

    item {
        SettingsSection(title = "Where games come from", isTablet = isTablet) {
            SettingsSectionNote(
                text = "Games are found on the channels in your playlists, using XMLTV guides, broadcast networks " +
                    "and fixture channel names. Add or edit playlists under Live TV. Your follows and channel " +
                    "picks are part of a settings backup, per profile; fetched games rebuild themselves.",
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
