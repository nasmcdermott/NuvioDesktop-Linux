package com.nuvio.app.features.sports

import com.nuvio.app.core.ui.arrowKeyFocusNavigation
import com.nuvio.app.core.ui.requestFocusLogged

import androidx.compose.ui.focus.focusRequester

import androidx.compose.ui.focus.FocusRequester

import androidx.compose.foundation.focusable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvio

/**
 * A game's own page: the scoreboard, and the channels showing it.
 *
 * The channels are the point. A game is not watchable until it has been matched to one of the
 * user's channels, so that list — with the reason each channel is there — is the main content
 * rather than a footnote under a poster.
 */
@Composable
internal fun SportsGameDetail(
    event: SportsEvent,
    league: League?,
    hideScores: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val options = remember(event.id) { SportsWatch.optionsFor(event) }
    // The box score is a second, heavier request; it loads behind the scoreboard the card already has.
    var summary by remember(event.id) { mutableStateOf<GameSummary?>(null) }
    var summaryFailed by remember(event.id) { mutableStateOf(false) }
    LaunchedEffect(event.id) {
        val loaded = SportsRepository.summary(event.leagueId, event.id)
        summary = loaded
        summaryFailed = loaded == null
    }

    // The page replaces the card rails, so it is the only thing on screen: it has to be able to hold
    // focus itself, or the arrow keys have nothing to act on and Compose reports focus as NONE.
    val pageFocus = remember { FocusRequester() }
    LaunchedEffect(event.id) {
        repeat(10) {
            if (pageFocus.requestFocusLogged()) return@LaunchedEffect
            kotlinx.coroutines.delay(48)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(pageFocus)
            .focusable()
            .arrowKeyFocusNavigation(releaseWhenStuck = true)
            .padding(tokens.spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.sectionGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = league?.name ?: "",
                style = MaterialTheme.typography.labelLarge,
                color = tokens.colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            SportsBackButton(onClick = onBack)
        }

        SportsScoreboard(event, hideScores)

        // Team comparison and the scoring summary, then who did what.
        summary?.let { loaded ->
            if (loaded.teamStats.isNotEmpty()) {
                Text(
                    text = "Team stats",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.colors.textPrimary,
                )
                SportsTeamStats(loaded.teamStats, event)
            }
            if (loaded.scoring.isNotEmpty()) {
                Text(
                    text = "Scoring",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.colors.textPrimary,
                )
                SportsScoringPlays(loaded.scoring)
            }
            if (loaded.categories.isNotEmpty()) {
                Text(
                    text = "Player stats",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.colors.textPrimary,
                )
                SportsPlayerStats(loaded.categories)
            }
        }
        if (summaryFailed) {
            Text(
                text = "Stats are not available for this game.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
            )
        }

        Text(
            text = when {
                options.isEmpty() -> "Not on your channels"
                options.size == 1 -> "On your channels"
                else -> "${options.size} of your channels"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = tokens.colors.textPrimary,
        )

        if (options.isEmpty()) {
            // Say what is missing rather than showing an empty list: the usual cause is no guide,
            // which is a fixable thing in Live TV settings.
            Text(
                text = "None of your playlists has a channel for this game. Add a guide in Live TV " +
                    "settings so games can be found by what is on at kick-off.",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textSecondary,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(tokens.spacing.listGap)) {
                items(options, key = { "${it.playlist.id}:${it.channel.key}" }) { option ->
                    SportsChannelOption(option) { SportsWatch.play(option) }
                }
            }
        }
    }
}

/** The scoreboard: both teams either side of the score, status underneath. */
@Composable
private fun SportsScoreboard(event: SportsEvent, hideScores: Boolean) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shapes.card)
            .background(tokens.colors.surfaceCard)
            .border(tokens.borders.hairline, tokens.colors.borderSubtle, tokens.shapes.card)
            .padding(tokens.spacing.cardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SportsScoreTeam(event.away, hideScores, Modifier.weight(1f))
            Text(
                text = if (hideScores) "–" else "${event.away.score ?: 0} – ${event.home.score ?: 0}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.colors.textPrimary,
            )
            SportsScoreTeam(event.home, hideScores, Modifier.weight(1f))
        }
        SportsStatusBadge(event)
        if (event.network != null) {
            Text(
                text = "Watching on ${event.network}",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textMuted,
            )
        }
    }
}

@Composable
private fun SportsScoreTeam(side: GameSide, hideScores: Boolean, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = side.team.shortName.ifBlank { side.team.name },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = tokens.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (side.record != null) {
            Text(text = side.record, style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted)
        }
    }
}

/** One channel that is showing the game. Clicking it plays it. */
@Composable
private fun SportsChannelOption(option: SportsWatch.Option, onPlay: () -> Unit) {
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
            .clickable(role = Role.Button, onClick = onPlay)
            .padding(tokens.spacing.cardPaddingCompact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = option.channel.name.ifBlank { "Channel ${option.channel.num}" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${option.playlist.name.ifBlank { "Playlist" }} · ${option.reason}",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "Watch",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (focused) tokens.colors.accent else tokens.colors.textSecondary,
        )
    }
}

/** Back out of the detail view; keyboard users have Escape/Back, this is for the pointer. */
@Composable
private fun SportsBackButton(onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .widthIn(min = 72.dp)
            .clip(RoundedCornerShape(50))
            .background(if (focused) tokens.colors.focusBackground else tokens.colors.surfaceCard)
            .border(
                tokens.borders.hairline,
                if (focused) tokens.colors.borderFocus else tokens.colors.borderSubtle,
                RoundedCornerShape(50),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = tokens.components.chipHorizontalPadding, vertical = tokens.components.chipVerticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Back", style = MaterialTheme.typography.labelLarge, color = tokens.colors.textPrimary)
    }
}

/** A section heading with an optional count, matching the LIVE NOW / COMING UP treatment. */
@Composable
internal fun SportsSectionHeader(
    title: String,
    count: Int?,
    live: Boolean,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
    ) {
        if (live) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(tokens.colors.danger)
                    .padding(4.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = tokens.colors.textSecondary,
        )
        if (count != null) {
            Spacer(Modifier.weight(1f))
            Text(
                text = if (count == 1) "1 game" else "$count games",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textMuted,
            )
        }
    }
}

/**
 * Away / label / home, the row shape a box score is read in.
 *
 * The leading side is emphasised rather than bar-charted: a bar for "3rd down efficiency" and a bar
 * for "Total yards" would imply a shared scale they do not have.
 */
@Composable
private fun SportsTeamStats(stats: List<TeamStatPair>, event: SportsEvent) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shapes.card)
            .background(tokens.colors.surfaceCard)
            .border(tokens.borders.hairline, tokens.colors.borderSubtle, tokens.shapes.card)
            .padding(tokens.spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = event.away.team.shortName,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = event.home.team.shortName,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textMuted,
                modifier = Modifier.weight(1f),
            )
        }
        stats.forEach { stat ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stat.away,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (stat.awayLeads) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (stat.awayLeads) tokens.colors.textPrimary else tokens.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stat.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.colors.textMuted,
                    modifier = Modifier.weight(1.4f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stat.home,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (stat.homeLeads) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (stat.homeLeads) tokens.colors.textPrimary else tokens.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** The scoring summary: period, clock, what happened, and the score it made. */
@Composable
private fun SportsScoringPlays(plays: List<ScoringPlay>) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shapes.card)
            .background(tokens.colors.surfaceCard)
            .border(tokens.borders.hairline, tokens.colors.borderSubtle, tokens.shapes.card)
            .padding(tokens.spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        plays.forEach { play ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    text = listOfNotNull(play.period, play.clock).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.colors.textMuted,
                    modifier = Modifier.widthIn(min = 72.dp),
                )
                Text(
                    text = play.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                if (play.awayScore != null && play.homeScore != null) {
                    Text(
                        text = "${play.awayScore}–${play.homeScore}",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.colors.textMuted,
                    )
                }
            }
        }
    }
}

/** Per-player categories: a heading, then each player's own line. */
@Composable
private fun SportsPlayerStats(categories: List<StatCategory>) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shapes.card)
            .background(tokens.colors.surfaceCard)
            .border(tokens.borders.hairline, tokens.colors.borderSubtle, tokens.shapes.card)
            .padding(tokens.spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { category ->
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = tokens.colors.textPrimary,
            )
            category.players.forEach { player ->
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = listOfNotNull(player.name, player.position).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textSecondary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (player.headline != null) {
                        Text(
                            text = player.headline,
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.colors.textMuted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
