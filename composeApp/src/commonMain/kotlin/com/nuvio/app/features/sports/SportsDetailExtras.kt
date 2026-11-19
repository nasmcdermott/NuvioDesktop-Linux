package com.nuvio.app.features.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.nuvio

// fork:sports — the two "around the game" cards the detail page adds under the box score: a team's
// season leaders and recent team news. Kept out of SportsGameDetail so upstream merges stay small.

/** The stat columns a player category shows, most useful first, capped at [max]. */
internal fun sportsPlayerStatColumns(labels: List<String>, max: Int = 5): List<Int> {
    if (labels.isEmpty()) return emptyList()
    if (labels.size <= max) return labels.indices.toList()
    val ranked = labels.indices
        .filter { labels[it].uppercase() in SPORTS_STAT_PRIORITY }
        .sortedBy { SPORTS_STAT_PRIORITY.indexOf(labels[it].uppercase()) }
        .take(max)
    // Keep the provider's own left-to-right order for the columns that were picked.
    return if (ranked.isNotEmpty()) ranked.sorted() else labels.indices.take(max).toList()
}

/**
 * The labels the player-stat table prefers when a provider sends more columns than fit. Ordered by
 * what a box score usually reads first; the table keeps each category's own order when showing them.
 */
private val SPORTS_STAT_PRIORITY = listOf(
    "PTS", "REB", "AST", "FG", "3PT", "FT", "MIN", "STL", "BLK",
    "C/ATT", "YDS", "TD", "INT", "CAR", "REC", "AVG",
    "G", "A", "S", "TOI", "+/-",
    "AB", "R", "H", "RBI", "HR", "K", "IP", "ER", "W", "SV",
)

/** Both teams' season leaders: one compact column per team, each with a player's picture. */
@Composable
internal fun SportsSeasonLeaders(
    event: SportsEvent,
    leaders: List<TeamLeaders>,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val byTeam = remember(leaders) { leaders.associateBy { it.teamId } }
    val away = byTeam[event.away.team.id]?.takeIf { it.categories.isNotEmpty() }
    val home = byTeam[event.home.team.id]?.takeIf { it.categories.isNotEmpty() }
    if (away == null && home == null) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(tokens.shapes.card)
            .background(tokens.colors.surfaceCard)
            .border(tokens.borders.hairline, tokens.colors.borderSubtle, tokens.shapes.card)
            .padding(tokens.spacing.cardPaddingCompact),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "SEASON LEADERS",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = tokens.colors.textSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (away != null) TeamLeadersColumn(event.away.team, away, Modifier.weight(1f))
            if (home != null) TeamLeadersColumn(event.home.team, home, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TeamLeadersColumn(team: Team, leaders: TeamLeaders, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TeamMark(team)
            Text(
                text = team.shortName.ifBlank { team.abbreviation },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = tokens.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        leaders.categories.forEach { category ->
            LeaderRow(category, team)
        }
    }
}

@Composable
private fun LeaderRow(category: TeamLeaderCategory, team: Team) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        PlayerAvatar(category.leader.name, category.leader.headshot, team)
        Column(Modifier.weight(1f)) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    category.leader.name,
                    category.leader.position?.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = category.leader.value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = tokens.colors.textPrimary,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Both teams' reported injuries, underneath season leaders. */
@Composable
internal fun SportsInjuryReport(
    event: SportsEvent,
    injuries: List<TeamInjuries>,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val byTeam = remember(injuries) { injuries.associateBy { it.teamId } }
    val ordered = listOf(event.away.team, event.home.team).mapNotNull { team ->
        byTeam[team.id]?.takeIf { it.reports.isNotEmpty() }?.let { team to it.reports }
    }
    if (ordered.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(tokens.shapes.card)
            .background(tokens.colors.surfaceCard)
            .border(tokens.borders.hairline, tokens.colors.borderSubtle, tokens.shapes.card)
            .padding(tokens.spacing.cardPaddingCompact),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = "INJURY REPORT",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = tokens.colors.textSecondary,
        )
        ordered.forEach { (team, reports) ->
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TeamMark(team)
                    Text(
                        text = team.shortName.ifBlank { team.abbreviation },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                reports.forEach { report -> InjuryRow(report, team) }
            }
        }
    }
}

@Composable
private fun InjuryRow(report: InjuryReport, team: Team) {
    val tokens = MaterialTheme.nuvio
    val statusColor = when {
        report.status == null -> tokens.colors.textSecondary
        report.status.contains("out", ignoreCase = true) ||
            report.status.contains("reserve", ignoreCase = true) -> tokens.colors.danger
        report.status.contains("day", ignoreCase = true) ||
            report.status.contains("question", ignoreCase = true) ||
            report.status.contains("doubtful", ignoreCase = true) -> tokens.colors.warning
        else -> tokens.colors.textSecondary
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        PlayerAvatar(report.name, report.headshot, team)
        Column(Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = listOfNotNull(report.name, report.position?.takeIf { it.isNotBlank() })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                report.status?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor,
                        maxLines = 1,
                    )
                }
            }
            val detail = listOfNotNull(
                report.injury,
                report.note?.takeIf { it != report.injury },
                report.side,
            ).joinToString(" · ")
            val meta = listOfNotNull(
                detail.takeIf { it.isNotBlank() },
                report.returnDate?.let(::injuryReturnText)?.let { "Return $it" },
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "2026-11-29" → "Nov 29"; the raw ISO date is not what a reader wants under a name. */
private fun injuryReturnText(raw: String): String? = runCatching {
    java.time.LocalDate.parse(raw)
        .format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.getDefault()))
}.getOrNull()

/** A player's headshot in a team-coloured circle; initials when the provider sends no picture. */
@Composable
private fun PlayerAvatar(name: String, headshotUrl: String?, team: Team) {
    val tokens = MaterialTheme.nuvio
    val headshot = headshotUrl?.takeIf { it.isNotBlank() }
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(team.color?.let(::Color) ?: tokens.colors.surfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        if (headshot != null) {
            NuvioAsyncImage(
                model = headshot,
                contentDescription = null,
                modifier = Modifier.size(26.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = name.split(' ').lastOrNull()?.take(1)?.uppercase() ?: "?",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

/** A team's crest at the head of its leaders column; a coloured abbreviation block if it has none. */
@Composable
private fun TeamMark(team: Team) {
    val tokens = MaterialTheme.nuvio
    val logo = team.logo?.takeIf { it.isNotBlank() }
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(team.color?.let(::Color) ?: tokens.colors.surfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            NuvioAsyncImage(
                model = logo,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                text = team.abbreviation.take(3),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

/** Recent stories for each team, under the moneyline panel: picture, headline and short body. */
@Composable
internal fun SportsTeamNews(
    event: SportsEvent,
    news: List<TeamNews>,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val byTeam = remember(news) { news.associateBy { it.teamId } }
    // The provider sometimes returns the same league story for both teams; show it once.
    val shown = mutableSetOf<String>()
    val ordered = listOf(event.away.team, event.home.team).mapNotNull { team ->
        val stories = byTeam[team.id]?.articles.orEmpty().filter { shown.add(it.id) }
        if (stories.isEmpty()) null else team to stories
    }
    if (ordered.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(tokens.shapes.card)
            .background(tokens.colors.surfaceCard)
            .border(tokens.borders.hairline, tokens.colors.borderSubtle, tokens.shapes.card)
            .padding(tokens.spacing.cardPaddingCompact),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = "NEWS",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = tokens.colors.textSecondary,
        )
        ordered.forEach { (team, articles) ->
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TeamMark(team)
                    Text(
                        text = team.shortName.ifBlank { team.abbreviation },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                articles.forEach { article -> NewsRow(article) }
            }
        }
    }
}

@Composable
private fun NewsRow(article: NewsArticle) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val image = article.image?.takeIf { it.isNotBlank() }
        if (image != null) {
            NuvioAsyncImage(
                model = image,
                contentDescription = null,
                modifier = Modifier
                    .width(82.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(7.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = article.headline,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = tokens.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            article.description?.takeIf { it.isNotBlank() }?.let { body ->
                Text(
                    text = body,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.colors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
