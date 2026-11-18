package com.nuvio.app.features.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.nuvio.app.core.ui.nuvio

/**
 * One game.
 *
 * The two teams are stacked with their scores against the right edge, which is the shape that
 * survives a narrow column: a side-by-side layout breaks as soon as a team name is long. The status
 * badge carries the clock or "Final", and the whole card is one focus stop so a gamepad never has to
 * step through a game's two halves to get past it.
 */
@Composable
internal fun SportsGameCard(
    event: SportsEvent,
    league: League?,
    hideScores: Boolean,
    /** True when a followed team is playing, so the card says why it is here. */
    followed: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    var focused by remember { mutableStateOf(false) }
    val shape = tokens.shapes.card
    val accent = league?.color?.let(::Color) ?: tokens.colors.accent

    Row(
        modifier = modifier
            .widthIn(min = 300.dp)
            .clip(shape)
            .background(if (focused) tokens.colors.focusBackground else tokens.colors.surfaceCard)
            .border(
                tokens.borders.hairline,
                when {
                    focused -> tokens.colors.borderFocus
                    event.isLive -> accent.copy(alpha = tokens.opacity.selected)
                    else -> tokens.colors.borderSubtle
                },
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(tokens.spacing.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
        ) {
            SportsTeamRow(event.away, hideScores, event.state, followed)
            SportsTeamRow(event.home, hideScores, event.state, followed)
            SportsGameFooter(event)
        }
        Spacer(Modifier.width(tokens.spacing.controlGap))
        Text(
            text = "›",
            style = MaterialTheme.typography.titleMedium,
            color = if (focused) tokens.colors.accent else tokens.colors.textMuted,
        )
    }
}

/** One side of the card: logo, name, score. */
@Composable
private fun SportsTeamRow(side: GameSide, hideScores: Boolean, state: GameState, followed: Boolean) {
    val tokens = MaterialTheme.nuvio
    val dimmed = state == GameState.POST && !side.winner
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamBadge(side.team)
        Spacer(Modifier.width(tokens.spacing.controlGap))
        Column(Modifier.weight(1f)) {
            Text(
                // A followed team's name carries a star, so My Sports rows explain themselves.
                text = (if (followed) "★ " else "") + side.team.shortName.ifBlank { side.team.name },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (state == GameState.POST && side.winner) FontWeight.SemiBold else FontWeight.Normal,
                color = if (dimmed) tokens.colors.textSecondary else tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (side.record != null) {
                Text(
                    text = side.record,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.colors.textMuted,
                )
            }
        }
        // Hide scores must not leave a gap where the score was, or the card looks broken rather
        // than private: a bullet stands in for the number.
        Text(
            text = when {
                hideScores -> "•"
                side.score != null -> side.score
                else -> ""
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (dimmed) tokens.colors.textSecondary else tokens.colors.textPrimary,
        )
    }
}

@Composable
private fun TeamBadge(team: Team) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = Modifier
            .size(tokens.icons.lg)
            .clip(RoundedCornerShape(4.dp))
            .background(team.color?.let(::Color) ?: tokens.colors.surfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        // The abbreviation stands in for the crest: no image loading in the list, which keeps a
        // grid of thirty games scrolling without a decoder queue.
        Text(
            text = team.abbreviation.take(4),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
        )
    }
}

/** The clock, "Final", kick-off time and network, under the teams. */
@Composable
private fun SportsGameFooter(event: SportsEvent) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
    ) {
        SportsStatusBadge(event)
        if (event.network != null) {
            Text(
                text = event.network,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "● 3rd · 9:42", "Final", or the kick-off time. */
@Composable
internal fun SportsStatusBadge(event: SportsEvent) {
    val tokens = MaterialTheme.nuvio
    val (text, color) = when (event.state) {
        GameState.LIVE -> (event.status ?: "Live") to tokens.colors.danger
        GameState.POST -> "Final" to tokens.colors.textMuted
        GameState.PRE -> (event.status ?: kickoffText(event.startMs)) to tokens.colors.textSecondary
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = tokens.opacity.subtle))
            .padding(horizontal = tokens.spacing.controlGap, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (event.state == GameState.LIVE) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
        }
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Local time of day for a kick-off: "7:30 PM" where the user is, not where the game is. */
internal fun kickoffText(startMs: Long): String {
    if (startMs <= 0L) return ""
    return java.time.Instant.ofEpochMilli(startMs)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.getDefault()))
}
