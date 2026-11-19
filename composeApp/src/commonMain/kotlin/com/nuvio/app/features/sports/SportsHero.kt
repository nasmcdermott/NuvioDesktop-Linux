package com.nuvio.app.features.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.arrowFocusDirection
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.smoothBorder

/**
 * The wide "NEXT FOR STEELERS · SUN 22 NOV" banner that leads My Sports: the next game of the page's
 * game set, drawn across two team-coloured halves with the match-up in the middle.
 *
 * A single keyboard/controller stop between the date strip and the game grid: Up returns to the
 * date strip, Down enters the grid.
 */
@Composable
internal fun SportsHero(
    event: SportsEvent,
    league: League?,
    hideScores: Boolean,
    /** Short name of the followed team the banner leads with, or null for a plain "Next up". */
    followedTeamName: String?,
    focusRequester: FocusRequester? = null,
    onUp: () -> Boolean,
    onDown: () -> Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val interaction = rememberSportsInteraction(baseScale = 1.03f, label = "sportsHeroZoom")
    val shape = RoundedCornerShape(22.dp)
    val isFieldEvent = event.away.team.id.endsWith(":event")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(188.dp)
            .onPreviewKeyEvent { keyEvent ->
                when (arrowFocusDirection(keyEvent)) {
                    FocusDirection.Up -> onUp()
                    FocusDirection.Down -> onDown()
                    else -> false
                }
            }
            .sportsInteractive(state = interaction, focusRequester = focusRequester, onClick = onClick)
            .clip(shape)
            .sportsMatchupGradientBackground(event)
            .smoothBorder(
                width = if (interaction.focused) 2.dp else tokens.borders.hairline,
                color = if (interaction.focused) tokens.colors.borderFocus else tokens.colors.borderSubtle,
                shape = shape,
            ),
    ) {
        Column(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 22.dp, vertical = 14.dp),
        ) {
            // Eyebrow, league mark, live clock. The two side slots take equal width, so the mark
            // lands on the banner's horizontal centre instead of wherever the eyebrow text ends.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = heroEyebrow(event, followedTeamName),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.1.sp,
                    color = tokens.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (league != null) SportsLeagueMark(league)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (event.state == GameState.LIVE) SportsStatusBadge(event, hideScores = hideScores)
                }
            }

            // The match-up. Both sides get an equal slot so the "AT" badge stays on the hero's
            // centre line even when one team's name or score is wider than the other's.
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                if (isFieldEvent) {
                    Text(
                        text = event.displayTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = tokens.colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HeroCrest(event.away.team)
                            Spacer(Modifier.width(14.dp))
                            HeroTeamName(event.away, event, hideScores)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = "AT",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = tokens.colors.textPrimary,
                        )
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HeroTeamName(event.home, event, hideScores, alignEnd = true)
                            Spacer(Modifier.width(14.dp))
                            HeroCrest(event.home.team)
                        }
                    }
                }
            }

            // NFL · Sun 22 Nov 7:00 PM · Lincoln Financial Field · CBS
            Text(
                text = listOfNotNull(
                    league?.shortName,
                    event.startMs.takeIf { it > 0L }?.let { kickoffDayText(it) },
                    event.venue,
                    event.network,
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.labelMedium,
                color = tokens.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HeroTeamName(
    side: GameSide,
    event: SportsEvent,
    hideScores: Boolean,
    alignEnd: Boolean = false,
) {
    val tokens = MaterialTheme.nuvio
    val showScore = !hideScores && event.state != GameState.PRE && side.score != null
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (alignEnd && showScore) {
            Text(
                text = side.score ?: "",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = tokens.colors.textPrimary,
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = side.team.shortName.ifBlank { side.team.name },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = tokens.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!alignEnd && showScore) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = side.score ?: "",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = tokens.colors.textPrimary,
            )
        }
    }
}

/** The big crest beside a hero team name; falls back to the abbreviation block. */
@Composable
private fun HeroCrest(team: Team) {
    val tokens = MaterialTheme.nuvio
    val logo = team.logo?.takeIf { it.isNotBlank() }
    Box(
        modifier = Modifier.size(52.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(team.color?.let(::Color) ?: tokens.colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            if (logo == null) {
                Text(
                    text = team.abbreviation.take(4),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
        logo?.let {
            NuvioAsyncImage(
                model = it,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/** The small league block at the hero's top-right corner. */
@Composable
private fun SportsLeagueMark(league: League) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color(league.color)),
        contentAlignment = Alignment.Center,
    ) {
        val logo = league.logo?.takeIf(String::isNotBlank)
        if (logo == null) {
            Text(
                text = league.shortName.take(5),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
            )
        } else {
            NuvioAsyncImage(
                model = logo,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/** "Next for Steelers · Sun 22 Nov", or "Next up · Sun 22 Nov" when no team leads the page. */
internal fun heroEyebrow(event: SportsEvent, followedTeamName: String?): String {
    val date = kickoffDayShortUpper(event.startMs)
    return if (followedTeamName.isNullOrBlank()) {
        "Next up · $date"
    } else {
        "Next for $followedTeamName · $date"
    }
}

/** The away/home vertical team-color halves shared by the hero and matchup detail cards. */
internal fun Modifier.sportsMatchupGradientBackground(
    event: SportsEvent,
    awayShare: Float = 0.5f,
): Modifier {
    val awayColor = event.away.team.color?.let(::Color) ?: Color(0xFF232A32)
    val homeColor = event.home.team.color?.let(::Color) ?: Color(0xFF232A32)
    return drawBehind {
        val splitX = size.width * awayShare.coerceIn(0.04f, 0.96f)
        drawRect(
            brush = Brush.verticalGradient(listOf(sportsTeamShade(awayColor, 0.34f), sportsTeamShade(awayColor, 0.08f))),
            topLeft = Offset.Zero,
            size = Size(splitX, size.height),
        )
        drawRect(
            brush = Brush.verticalGradient(listOf(sportsTeamShade(homeColor, 0.62f), sportsTeamShade(homeColor, 0.28f))),
            topLeft = Offset(splitX, 0f),
            size = Size(size.width - splitX, size.height),
        )
    }
}

/** Mixes a colour toward black by [strength] (1f = unchanged), for the hero's panel shading. */
internal fun sportsTeamShade(color: Color, strength: Float): Color = Color(
    red = color.red * strength,
    green = color.green * strength,
    blue = color.blue * strength,
    alpha = 1f,
)
