package com.nuvio.app.features.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioModalDialog
import com.nuvio.app.core.ui.NuvioTextField
import com.nuvio.app.core.ui.nuvio

/**
 * Follow teams and leagues.
 *
 * Leagues come from the catalog (instant, no network); a league's teams are fetched the first time
 * it is opened and cached for a week by the repository, so the list is usable while a league is
 * still loading rather than blocking on it.
 *
 * The search box filters both at once: a viewer who knows they want "Arsenal" should not have to
 * work out which league to open first.
 */
@Composable
internal fun SportsFollowPicker(
    onDismiss: () -> Unit,
    onNavigateToLiveTv: (() -> Unit)? = null,
) {
    val tokens = MaterialTheme.nuvio
    val settings by SportsSettingsRepository.settings.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    // League id -> its teams, as they arrive. Fetching is per league and guarded by the repository.
    val teamsByLeague = remember { mutableStateMapOf<String, List<Team>>() }
    val loading = remember { mutableStateMapOf<String, Boolean>() }
    val leagues = remember { SportsCatalog.leagues }

    val needle = remember(query) { query.trim().lowercase() }
    val matchingLeagues = remember(needle, leagues) {
        if (needle.isEmpty()) leagues
        else leagues.filter { league ->
            league.name.lowercase().contains(needle) ||
                league.shortName.lowercase().contains(needle) ||
                league.guideWords.any { it.lowercase().contains(needle) }
        }
    }

    // Open the picker on the user's followed things, so it reads as a summary as well as a picker.
    LaunchedEffect(Unit) {
        leagues.forEach { league -> if (league.id in settings.followedLeagues) loadTeams(league.id, teamsByLeague, loading) }
    }

    val followedCount = SportsFollows.count(settings)

    NuvioModalDialog(
        onDismissRequest = onDismiss,
        title = "Follow teams and leagues",
        subtitle = if (followedCount == 0) {
            "Pick the teams and competitions you care about. Their games lead the Sports page."
        } else {
            "$followedCount followed. Their games lead the Sports page."
        },
        maxWidth = 720.dp,
        actions = {
            com.nuvio.app.features.settings.SettingsGlassButton(
                text = if (followedCount == 0) "Done" else "Clear",
                onClick = {
                    if (followedCount == 0) onDismiss() else SportsSettingsRepository.clearFollows()
                },
            )
            com.nuvio.app.features.settings.SettingsGlassButton(
                text = "Done",
                onClick = onDismiss,
                accent = true,
            )
        },
    ) {
        NuvioTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Search teams and leagues",
        )

        // Flattened into rows before the list: LazyColumn items cannot nest, and a league's teams
        // are part of the same scroll rather than a sub-list.
        val rows = buildList<PickerRow> {
            matchingLeagues.forEach { league ->
                val followed = SportsFollows.isLeagueFollowed(settings, league.id)
                val expanded = needle.isNotEmpty() || followed || league.id in teamsByLeague
                if (expanded) loadTeams(league.id, teamsByLeague, loading)
                add(PickerRow.LeagueRow(league, followed))
                if (!expanded) return@forEach
                val teams = teamsByLeague[league.id]
                when {
                    loading[league.id] == true && teams == null ->
                        add(PickerRow.NoteRow("Loading ${league.name} teams…", spinner = true))
                    teams.isNullOrEmpty() ->
                        add(PickerRow.NoteRow("No teams available for ${league.name}."))
                    else -> {
                        val shown = if (needle.isEmpty()) {
                            teams
                        } else {
                            teams.filter { team ->
                                team.name.lowercase().contains(needle) ||
                                    team.shortName.lowercase().contains(needle) ||
                                    team.abbreviation.lowercase().contains(needle)
                            }
                        }
                        if (shown.isEmpty() && needle.isNotEmpty()) {
                            add(PickerRow.NoteRow("No ${league.name} team matches “$query”."))
                        } else {
                            shown.forEach { team ->
                                add(
                                    PickerRow.TeamRow(
                                        leagueId = league.id,
                                        team = team,
                                        followed = SportsFollows.isTeamFollowed(settings, league.id, team.id),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
        ) {
            items(rows, key = { it.key }) { row ->
                when (row) {
                    is PickerRow.LeagueRow -> SportsPickerLeagueHeader(
                        league = row.league,
                        followed = row.followed,
                        onToggleFollow = {
                            SportsSettingsRepository.setLeagueFollowed(row.league.id, !row.followed)
                        },
                    )
                    is PickerRow.TeamRow -> SportsPickerTeamRow(
                        team = row.team,
                        followed = row.followed,
                        onToggle = {
                            SportsSettingsRepository.setTeamFollowed(
                                SportsFollows.teamKey(row.leagueId, row.team.id),
                                !row.followed,
                            )
                        },
                    )
                    is PickerRow.NoteRow -> SportsPickerNote(row.text, showSpinner = row.spinner)
                }
            }
        }

        if (onNavigateToLiveTv != null) {
            Text(
                text = "Games are found on your Live TV channels. Add a playlist under Live TV.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onNavigateToLiveTv)
                    .padding(top = tokens.spacing.controlGap),
            )
        }
    }
}

/** One line of the picker: a league header, one of its teams, or a note under either. */
private sealed interface PickerRow {
    val key: String

    data class LeagueRow(val league: League, val followed: Boolean) : PickerRow {
        override val key = "league:${league.id}"
    }

    data class TeamRow(val leagueId: String, val team: Team, val followed: Boolean) : PickerRow {
        override val key = "team:$leagueId:${team.id}"
    }

    data class NoteRow(val text: String, val spinner: Boolean = false) : PickerRow {
        override val key = "note:$text"
    }
}

/** Teams are fetched once per league and kept for the life of the picker. */
private fun loadTeams(
    leagueId: String,
    into: MutableMap<String, List<Team>>,
    loading: MutableMap<String, Boolean>,
) {
    if (into.containsKey(leagueId) || loading[leagueId] == true) return
    loading[leagueId] = true
    SportsRepository.teams(leagueId) { teams ->
        into[leagueId] = teams
        loading.remove(leagueId)
    }
}

/** A league row: the follow toggle for the whole competition. */
@Composable
private fun SportsPickerLeagueHeader(
    league: League,
    followed: Boolean,
    onToggleFollow: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shapes.compactCard)
            .background(if (focused) tokens.colors.focusBackground else tokens.colors.surfaceCard)
            .border(
                tokens.borders.hairline,
                if (focused) tokens.colors.borderFocus else tokens.colors.borderSubtle,
                tokens.shapes.compactCard,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Checkbox, onClick = onToggleFollow)
            .padding(tokens.spacing.cardPaddingCompact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
    ) {
        Box(
            Modifier
                .size(tokens.icons.sm)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(league.color)),
        )
        Text(
            text = league.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = tokens.colors.textPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (followed) "Following" else "Follow league",
            style = MaterialTheme.typography.labelSmall,
            color = if (followed) tokens.colors.accent else tokens.colors.textMuted,
        )
        SportsFollowCheck(followed)
    }
}

/** A team row, indented under its league. */
@Composable
private fun SportsPickerTeamRow(team: Team, followed: Boolean, onToggle: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = tokens.icons.md)
            .clip(tokens.shapes.compactCard)
            .background(if (focused) tokens.colors.focusBackground else Color.Transparent)
            .border(
                tokens.borders.hairline,
                if (focused) tokens.colors.borderFocus else Color.Transparent,
                tokens.shapes.compactCard,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .padding(tokens.spacing.cardPaddingCompact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
    ) {
        Box(
            Modifier
                .size(tokens.icons.sm)
                .clip(RoundedCornerShape(3.dp))
                .background(team.color?.let(::Color) ?: tokens.colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = team.abbreviation.take(4),
                fontSize = 8.sp,
                color = Color.White,
                maxLines = 1,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = team.name,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = team.abbreviation,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textMuted,
            )
        }
        SportsFollowCheck(followed)
    }
}

/** The tick, or an empty slot so rows do not jump when one is followed. */
@Composable
private fun SportsFollowCheck(followed: Boolean) {
    val tokens = MaterialTheme.nuvio
    Box(Modifier.size(tokens.icons.md), contentAlignment = Alignment.Center) {
        if (followed) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "Following",
                tint = tokens.colors.accent,
                modifier = Modifier.size(tokens.icons.sm),
            )
        }
    }
}

@Composable
private fun SportsPickerNote(text: String, showSpinner: Boolean = false) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = tokens.icons.md, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
    ) {
        if (showSpinner) {
            CircularProgressIndicator(
                modifier = Modifier.size(tokens.icons.xs),
                strokeWidth = 1.dp,
                color = tokens.colors.textMuted,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = tokens.colors.textMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 520.dp),
        )
    }
}
