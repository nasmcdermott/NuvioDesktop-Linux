package com.nuvio.app.features.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NavAction
import com.nuvio.app.core.ui.navAction
import com.nuvio.app.core.ui.nuvio

/** Games or Tables: the two things this page shows, and the only switch that swaps them. */
@Composable
internal fun SportsModeToggle(tablesMode: Boolean, onSelect: (Boolean) -> Unit) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .clip(tokens.shapes.chip)
            .background(tokens.colors.surfaceCard)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SportsModeOption("Games", selected = !tablesMode) { onSelect(false) }
        SportsModeOption("Tables", selected = tablesMode) { onSelect(true) }
    }
}

@Composable
private fun SportsModeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    var focused by remember { mutableStateOf(false) }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = if (selected) tokens.colors.onAccent else tokens.colors.textPrimary,
        modifier = Modifier
            .clip(tokens.shapes.chip)
            .background(if (selected) tokens.colors.accent else tokens.colors.surfaceCard)
            .then(
                if (focused && !selected) Modifier.border(tokens.borders.hairline, tokens.colors.borderFocus, tokens.shapes.chip)
                else Modifier,
            )
            .focusable()
            .onKeyEvent { event ->
                val activates = event.navAction() == NavAction.Select || event.key == Key.Spacebar
                when {
                    !activates -> false
                    event.type == KeyEventType.KeyDown -> true
                    event.type == KeyEventType.KeyUp -> { onClick(); true }
                    else -> true
                }
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = tokens.spacing.cardPaddingCompact, vertical = 4.dp),
    )
}

/**
 * The league tables.
 *
 * One table per league, the league's own name above it: the feed's columns differ too much between
 * sports for a single shared table to mean anything. A followed league's table leads, the same way
 * its games lead the other mode.
 */
@Composable
internal fun SportsTablesView(
    leagues: List<League>,
    mySportsOnly: Boolean,
    settings: SportsSettings,
) {
    val tokens = MaterialTheme.nuvio
    // Only leagues that can actually have a table: the feed has none for motor racing or golf, and
    // asking would be a request per those leagues on every open for a guaranteed empty answer.
    val candidates = remember(leagues, mySportsOnly, settings.followedLeagues) {
        leagues
            // Racing, golf, darts and the like have no standings on this feed.
            .filter { it.sport !in NO_TABLE_SPORTS }
            .filter { !mySportsOnly || settings.followedLeagues.contains(it.id) }
            .sortedByDescending { settings.followedLeagues.contains(it.id) }
    }

    if (candidates.isEmpty()) {
        BoxedNotice(
            title = "No tables to show",
            body = if (mySportsOnly) {
                "Follow a league to see its table here."
            } else {
                "Tables are not available for the leagues on this page."
            },
        )
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sectionGap)) {
        items(candidates, key = { it.id }) { league ->
            SportsLeagueTable(league)
        }
    }
}

@Composable
private fun SportsLeagueTable(league: League) {
    val tokens = MaterialTheme.nuvio
    var rows by remember(league.id) { mutableStateOf<List<Standing>?>(null) }
    var failed by remember(league.id) { mutableStateOf(false) }
    LaunchedEffect(league.id) {
        val loaded = SportsRepository.standings(league.id)
        rows = loaded
        // null is "no table for this league", an empty list means the request failed. Both end up
        // as a one-line note; only one of them is worth retrying.
        failed = loaded == null
    }

    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap)) {
        Text(
            text = league.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = tokens.colors.textPrimary,
        )
        val current = rows
        when {
            current == null && !failed -> Text(
                text = "Loading…",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
            )
            current.isNullOrEmpty() -> Text(
                text = "No table for this league yet.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
            )
            else -> SportsTableBody(current)
        }
    }
}

@Composable
private fun SportsTableBody(rows: List<Standing>) {
    val tokens = MaterialTheme.nuvio
    val columns = remember(rows) { SportsTable.columns(rows) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shapes.card)
            .background(tokens.colors.surfaceCard)
            .border(tokens.borders.hairline, tokens.colors.borderSubtle, tokens.shapes.card)
            .padding(vertical = tokens.spacing.controlGap),
    ) {
        SportsTableHeaderRow(columns.size)
        LazyRow {
            item {
                Column(Modifier.fillMaxWidth()) {
                    rows.forEachIndexed { index, row ->
                        SportsTableRow(row = row, columns = columns, odd = index % 2 == 1)
                    }
                }
            }
        }
    }
}

/** The column strip: a team column and one heading per value column. */
@Composable
private fun SportsTableHeaderRow(columnCount: Int) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.cardPaddingCompact, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Team",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.colors.textMuted,
            modifier = Modifier.weight(1f),
        )
        repeat(columnCount) {
            // Value columns share a fixed width so every row lines up under every heading.
            Text(
                text = "",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(ColumnWidth),
            )
        }
    }
}

@Composable
private fun SportsTableRow(row: Standing, columns: List<SportsTable.Column>, odd: Boolean) {
    val tokens = MaterialTheme.nuvio
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    focused -> tokens.colors.focusBackground
                    odd -> tokens.colors.surfaceElevated.copy(alpha = tokens.opacity.subtle)
                    else -> tokens.colors.surfaceCard
                },
            )
            .padding(horizontal = tokens.spacing.cardPaddingCompact, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.team.shortName.ifBlank { row.team.name },
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        SportsTable.cells(row, columns).forEach { value ->
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = if (value == SportsTable.MISSING) tokens.colors.textMuted else tokens.colors.textPrimary,
                textAlign = TextAlign.End,
                modifier = Modifier.width(ColumnWidth),
            )
        }
    }
}

/** Sports whose feed publishes no standings at all, so the Tables view skips them. */
private val NO_TABLE_SPORTS = setOf(
    Sport.RACING, Sport.GOLF, Sport.DARTS, Sport.SNOOKER, Sport.FIGHTING,
    Sport.WRESTLING, Sport.TENNIS, Sport.CRICKET,
)

/** Wide enough for "+88", ".889" and "W5"; the table never needs horizontal scrolling. */
private val ColumnWidth = 52.dp

@Composable
private fun BoxedNotice(title: String, body: String) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = Modifier.fillMaxWidth().padding(tokens.spacing.sectionGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted)
    }
}
