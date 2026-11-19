package com.nuvio.app.features.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.arrowFocusDirection
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.requestFocusLogged
import com.nuvio.app.core.ui.smoothBorder
import kotlinx.coroutines.delay

/** Request to open the Tables view focused on a specific league (e.g. from a game page). */
internal data class TableOpenRequest(
    val leagueId: String,
    val markedTeamIds: Set<String> = emptySet(),
    val returnToGameId: String? = null,
)

/**
 * League standings tables, wired into the vertical and horizontal keyboard/controller focus ladder:
 *  Sport Chips ⇄ League Strip ⇄ (Division/Conference Strip) ⇄ Table Rows ⇄ Team Summary Card
 */
@Composable
internal fun SportsTablesView(
    leagues: List<League>,
    mySportsOnly: Boolean,
    settings: SportsSettings,
    request: TableOpenRequest? = null,
    leagueStripFocus: FocusRequester = remember { FocusRequester() },
    onUpToChips: () -> Boolean = { false },
    onBackToGame: (() -> Unit)? = null,
    onOpenGame: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val followedTeamLeagues = remember(settings.followedTeams) {
        settings.followedTeams.mapNotNullTo(HashSet()) { SportsFollows.leagueOfTeamKey(it) }
    }
    var noTableIds by remember { mutableStateOf(emptySet<String>()) }

    val candidates = remember(leagues, mySportsOnly, settings.followedLeagues, followedTeamLeagues, request?.leagueId, noTableIds) {
        val base = leagues
            .filter { it.sport !in NO_TABLE_SPORTS }
            .filter { !mySportsOnly || it.id in settings.followedLeagues || it.id in followedTeamLeagues }
            .sortedWith(
                compareByDescending<League> { it.id in settings.followedLeagues || it.id in followedTeamLeagues }
                    .thenBy { leagues.indexOf(it) },
            )
        val requested = request?.leagueId?.let(SportsCatalog::league)
        val withRequested = if (requested != null && base.none { it.id == requested.id }) {
            listOf(requested) + base
        } else {
            base
        }
        withRequested.filter { it.id !in noTableIds || it.id == request?.leagueId }
    }

    if (candidates.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event -> arrowFocusDirection(event) == FocusDirection.Up && onUpToChips() },
        ) {
            BoxedNotice(
                title = "No tables to show",
                body = if (mySportsOnly) {
                    "Follow a team or league to see its standings here."
                } else {
                    "Standings are not available for the selected sport."
                },
            )
        }
        return
    }

    var selectedLeagueId by rememberSaveable(request?.leagueId) {
        mutableStateOf(request?.leagueId ?: candidates.first().id)
    }
    val activeLeague = candidates.firstOrNull { it.id == selectedLeagueId } ?: candidates.first()
    if (activeLeague.id != selectedLeagueId) {
        selectedLeagueId = activeLeague.id
    }

    val loadedResults = remember { mutableStateMapOf<String, SportsRepository.StandingsResult>() }
    var retryCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(activeLeague.id, retryCount) {
        val force = retryCount > 0 && loadedResults[activeLeague.id] is SportsRepository.StandingsResult.Failed
        val res = SportsRepository.loadStandings(activeLeague.id, force = force)
        loadedResults[activeLeague.id] = res
        if (res is SportsRepository.StandingsResult.NoTable) {
            noTableIds = noTableIds + activeLeague.id
        }
    }

    LaunchedEffect(candidates) {
        delay(350)
        for (league in candidates) {
            if (league.id == activeLeague.id || league.id in loadedResults) continue
            val res = SportsRepository.loadStandings(league.id)
            loadedResults[league.id] = res
            if (res is SportsRepository.StandingsResult.NoTable) {
                noTableIds = noTableIds + league.id
            }
        }
    }

    val divisionBandFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    var enterTableBodyRequests by remember { mutableIntStateOf(0) }

    val currentResult = loadedResults[activeLeague.id]
    val activeLoadedTable = (currentResult as? SportsRepository.StandingsResult.Loaded)?.let { loaded ->
        val recentGames = remember(activeLeague.id, loaded.rows) { SportsRepository.knownGames(activeLeague.id) }
        remember(activeLeague, loaded.rows, recentGames) {
            Triple(SportsTable.build(activeLeague, loaded.rows, recentGames), recentGames, loaded.stale)
        }
    }

    // When opened directly from a game page, seat focus onto the league strip or marked row.
    LaunchedEffect(request?.leagueId) {
        if (request?.leagueId != null) {
            delay(80)
            enterTableBodyRequests++
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
    ) {
        SportsLeagueTableStrip(
            leagues = candidates,
            selectedId = activeLeague.id,
            followedLeagues = settings.followedLeagues,
            followedTeamLeagues = followedTeamLeagues,
            bandRequester = leagueStripFocus,
            onBackToGame = if (request?.returnToGameId != null) onBackToGame else null,
            onSelect = { selectedLeagueId = it },
            onUp = onUpToChips,
            onDown = {
                when {
                    settings.hideScores -> false
                    currentResult is SportsRepository.StandingsResult.Failed -> retryFocus.requestFocusLogged()
                    activeLoadedTable?.first?.hasDivisions == true -> divisionBandFocus.requestFocusLogged()
                    activeLoadedTable != null -> {
                        enterTableBodyRequests++
                        true
                    }
                    else -> true
                }
            },
        )

        if (settings.hideScores) {
            BoxedNotice(
                title = "Scores and tables are hidden",
                body = "Turn off Hide scores in Sports settings to view league standings.",
            )
            return@Column
        }

        when {
            currentResult == null -> BoxedNotice(
                title = "Loading ${activeLeague.name} table…",
                body = "Fetching current standings.",
            )
            currentResult is SportsRepository.StandingsResult.NoTable -> BoxedNotice(
                title = "No standings for ${activeLeague.name}",
                body = "Knockout cups and single-event competitions do not publish a league table.",
            )
            currentResult is SportsRepository.StandingsResult.Failed -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(tokens.spacing.sectionGap)
                    .onPreviewKeyEvent { ev ->
                        arrowFocusDirection(ev) == FocusDirection.Up && leagueStripFocus.requestFocusLogged()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
            ) {
                Text(
                    text = "Could not load ${activeLeague.name} standings",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.colors.textPrimary,
                )
                Text(
                    text = "Check your connection and try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textMuted,
                )
                SportsSmallPill(
                    label = "Retry",
                    selected = true,
                    focusRequester = retryFocus,
                    onClick = { retryCount++ },
                )
            }
            activeLoadedTable != null -> {
                val (table, recentGames, stale) = activeLoadedTable
                val followedIdsInLeague = remember(activeLeague.id, settings.followedTeams) {
                    settings.followedTeams.mapNotNullTo(HashSet()) { key ->
                        if (SportsFollows.leagueOfTeamKey(key) == activeLeague.id) key.substringAfter(':') else null
                    }
                }
                val markedIds = remember(activeLeague.id, request, followedIdsInLeague) {
                    val requestedMarks = if (request?.leagueId == activeLeague.id) request.markedTeamIds else emptySet()
                    followedIdsInLeague + requestedMarks
                }
                SportsLeagueTableContent(
                    league = activeLeague,
                    table = table,
                    stale = stale,
                    settings = settings,
                    followedIds = followedIdsInLeague,
                    markedIds = markedIds,
                    recentGames = recentGames,
                    divisionBandFocus = divisionBandFocus,
                    enterTableBodyRequests = enterTableBodyRequests,
                    onUpToLeagueStrip = { leagueStripFocus.requestFocusLogged() },
                    onOpenGame = onOpenGame,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SportsLeagueTableStrip(
    leagues: List<League>,
    selectedId: String,
    followedLeagues: Set<String>,
    followedTeamLeagues: Set<String>,
    bandRequester: FocusRequester,
    onBackToGame: (() -> Unit)?,
    onSelect: (String) -> Unit,
    onUp: () -> Boolean,
    onDown: () -> Boolean,
) {
    val tokens = MaterialTheme.nuvio
    val pillRequesters = remember { HashMap<String, FocusRequester>() }
    fun requesterFor(id: String): FocusRequester = pillRequesters.getOrPut(id) { FocusRequester() }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .sportsHorizontalBand(
                bandRequester = bandRequester,
                fallbackRequester = requesterFor(selectedId),
                onUp = onUp,
                onDown = onDown,
            ),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBackToGame != null) {
            item(key = "back-to-game") {
                SportsSmallPill(
                    label = "‹ Back to game",
                    selected = false,
                    onClick = onBackToGame,
                )
            }
        }
        items(leagues, key = { it.id }) { league ->
            val selected = league.id == selectedId
            val isMine = league.id in followedLeagues || league.id in followedTeamLeagues
            SportsLeaguePill(
                league = league,
                selected = selected,
                starred = isMine,
                focusRequester = requesterFor(league.id),
                onClick = { onSelect(league.id) },
            )
        }
    }
}

@Composable
private fun SportsLeaguePill(
    league: League,
    selected: Boolean,
    starred: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val interaction = rememberSportsInteraction(baseScale = 1.05f, label = "sportsLeaguePillZoom")
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .sportsInteractive(
                state = interaction,
                focusRequester = focusRequester,
                onClick = onClick,
            )
            .clip(shape)
            .background(
                when {
                    selected -> tokens.colors.accentFill
                    interaction.highlighted -> SolidColor(tokens.colors.focusBackground)
                    else -> SolidColor(tokens.colors.surfaceCard)
                },
            )
            .smoothBorder(
                width = if (interaction.focused) 2.dp else tokens.borders.hairline,
                color = when {
                    interaction.focused -> tokens.colors.borderFocus
                    selected -> tokens.colors.borderSelected
                    else -> tokens.colors.borderSubtle
                },
                shape = shape,
            )
            .padding(horizontal = tokens.components.chipHorizontalPadding, vertical = tokens.components.chipVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(league.color)),
            contentAlignment = Alignment.Center,
        ) {
            val logo = league.logo?.takeIf(String::isNotBlank)
            if (logo == null) {
                Text(
                    text = league.shortName.take(3),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                )
            } else {
                NuvioAsyncImage(
                    model = logo,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Text(
            text = (if (starred) "★ " else "") + league.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected || interaction.focused) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) tokens.colors.onAccent else tokens.colors.textPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun SportsLeagueTableContent(
    league: League,
    table: SportsTable.TableData,
    stale: Boolean,
    settings: SportsSettings,
    followedIds: Set<String>,
    markedIds: Set<String>,
    recentGames: List<SportsEvent>,
    divisionBandFocus: FocusRequester,
    enterTableBodyRequests: Int,
    onUpToLeagueStrip: () -> Boolean,
    onOpenGame: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    var view by rememberSaveable(league.id) { mutableStateOf(SportsTable.View.DIVISION) }
    var conference by rememberSaveable(league.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(league.id, table.conferences, markedIds) {
        if (table.hasDivisions && (conference == null || conference !in table.conferences)) {
            conference = markedIds.firstNotNullOfOrNull { id -> table.findTeam(id)?.first?.conference }
                ?: table.conferences.firstOrNull()
        }
    }

    val groups = remember(table, view, conference) { table.groupsFor(view, conference) }
    val cutLines = remember(table, view) { table.playoffLines(view) }
    val mergedView = table.hasDivisions && view != SportsTable.View.DIVISION

    var focusedRow by remember(league.id) {
        mutableStateOf(
            markedIds.firstNotNullOfOrNull { id -> table.findTeam(id)?.second }
                ?: groups.firstOrNull()?.rows?.firstOrNull(),
        )
    }
    LaunchedEffect(groups, markedIds) {
        val currentId = focusedRow?.team?.id
        if (currentId == null || groups.none { g -> g.rows.any { it.team.id == currentId } }) {
            focusedRow = markedIds.firstNotNullOfOrNull { id ->
                groups.firstNotNullOfOrNull { g -> g.rows.firstOrNull { it.team.id == id } }
            } ?: groups.firstOrNull()?.rows?.firstOrNull()
        }
    }

    val divisionFirstFocus = remember { FocusRequester() }
    val sideCardBandFocus = remember { FocusRequester() }
    var focusTableRowsTrigger by remember { mutableIntStateOf(0) }
    var refocusActiveRowTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(enterTableBodyRequests) {
        if (enterTableBodyRequests > 0) focusTableRowsTrigger++
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
    ) {
        if (table.hasDivisions || stale) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
            ) {
                if (table.hasDivisions) {
                    LazyRow(
                        modifier = Modifier
                            .weight(1f)
                            .sportsHorizontalBand(
                                bandRequester = divisionBandFocus,
                                fallbackRequester = divisionFirstFocus,
                                onUp = onUpToLeagueStrip,
                                onDown = {
                                    focusTableRowsTrigger++
                                    true
                                },
                            ),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(SportsTable.View.entries, key = { "view:${it.name}" }) { option ->
                            SportsSmallPill(
                                label = option.label,
                                selected = view == option,
                                focusRequester = if (option == SportsTable.View.DIVISION) divisionFirstFocus else null,
                                onClick = { view = option },
                            )
                        }
                        if (view != SportsTable.View.LEAGUE && table.conferences.size > 1) {
                            item(key = "conf-spacer") { Spacer(Modifier.width(8.dp)) }
                            items(table.conferences, key = { "conf:$it" }) { conf ->
                                val activeConf = conference ?: table.conferences.first()
                                SportsSmallPill(
                                    label = SportsTable.shortGroupName(conf),
                                    selected = activeConf == conf,
                                    onClick = { conference = conf },
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (stale) {
                    Text(
                        text = "Showing cached standings",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.colors.warning,
                    )
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val wideLayout = maxWidth >= 920.dp
            val upFromTable: () -> Boolean = {
                if (table.hasDivisions) divisionBandFocus.requestFocusLogged()
                else onUpToLeagueStrip()
            }

            if (wideLayout) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sectionGap),
                ) {
                    SportsStandingsTableCard(
                        league = league,
                        table = table,
                        groups = groups,
                        cutLines = cutLines,
                        mergedView = mergedView,
                        followedIds = followedIds,
                        markedIds = markedIds,
                        focusTrigger = focusTableRowsTrigger,
                        refocusActiveTrigger = refocusActiveRowTrigger,
                        onUpFromFirstRow = upFromTable,
                        onRightToSideCard = { sideCardBandFocus.requestFocusLogged() },
                        onFocusRow = { focusedRow = it },
                        onActivateRow = { row ->
                            focusedRow = row
                            if (!row.athlete && row.team.id.isNotBlank()) {
                                val key = SportsFollows.teamKey(league.id, row.team.id)
                                SportsSettingsRepository.setTeamFollowed(key, key !in settings.followedTeams)
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    Column(
                        modifier = Modifier.width(320.dp).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
                    ) {
                        focusedRow?.let { row ->
                            SportsTeamStandingCard(
                                table = table,
                                row = row,
                                followed = row.team.id in followedIds,
                                settings = settings,
                                recentGames = recentGames,
                                bandRequester = sideCardBandFocus,
                                onUp = upFromTable,
                                onLeftToTable = {
                                    refocusActiveRowTrigger++
                                    true
                                },
                                onToggleFollow = {
                                    if (!row.athlete && row.team.id.isNotBlank()) {
                                        val key = SportsFollows.teamKey(league.id, row.team.id)
                                        SportsSettingsRepository.setTeamFollowed(key, key !in settings.followedTeams)
                                    }
                                },
                                onOpenGame = onOpenGame,
                            )
                        }
                        SportsTableLegend(table = table, cutLines = cutLines)
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
                ) {
                    SportsStandingsTableCard(
                        league = league,
                        table = table,
                        groups = groups,
                        cutLines = cutLines,
                        mergedView = mergedView,
                        followedIds = followedIds,
                        markedIds = markedIds,
                        focusTrigger = focusTableRowsTrigger,
                        refocusActiveTrigger = refocusActiveRowTrigger,
                        onUpFromFirstRow = upFromTable,
                        onRightToSideCard = null,
                        onFocusRow = { focusedRow = it },
                        onActivateRow = { row ->
                            focusedRow = row
                            if (!row.athlete && row.team.id.isNotBlank()) {
                                val key = SportsFollows.teamKey(league.id, row.team.id)
                                SportsSettingsRepository.setTeamFollowed(key, key !in settings.followedTeams)
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    SportsTableLegend(table = table, cutLines = cutLines, compact = true)
                }
            }
        }
    }
}

private sealed interface TableLazyEntry {
    val key: String
    data class GroupHeader(override val key: String, val title: String) : TableLazyEntry
    data class Cut(override val key: String, val label: String) : TableLazyEntry
    data class TeamRow(
        override val key: String,
        val row: Standing,
        val rowIndex: Int,
        val lazyIndex: Int,
        val odd: Boolean,
    ) : TableLazyEntry
}

@Composable
private fun SportsStandingsTableCard(
    league: League,
    table: SportsTable.TableData,
    groups: List<SportsTable.Group>,
    cutLines: List<SportsTable.CutLine>,
    mergedView: Boolean,
    followedIds: Set<String>,
    markedIds: Set<String>,
    focusTrigger: Int,
    refocusActiveTrigger: Int,
    onUpFromFirstRow: () -> Boolean,
    onRightToSideCard: (() -> Boolean)?,
    onFocusRow: (Standing) -> Unit,
    onActivateRow: (Standing) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val listState = rememberLazyListState()
    val listFocus = rememberSportsListFocus(listState)

    val builtEntries = remember(groups, cutLines, table.hasDivisions) {
        val entries = ArrayList<TableLazyEntry>()
        val rowToLazyIndex = ArrayList<Int>()
        var rowCounter = 0
        groups.forEachIndexed { gi, group ->
            if (groups.size > 1 || table.hasDivisions) {
                entries += TableLazyEntry.GroupHeader("hdr:$gi", SportsTable.shortGroupName(group.name).uppercase())
            }
            group.rows.forEachIndexed { ri, row ->
                val lazyIdx = entries.size
                val rIdx = rowCounter++
                rowToLazyIndex += lazyIdx
                entries += TableLazyEntry.TeamRow(
                    key = "row:$gi:${row.team.id}:$ri",
                    row = row,
                    rowIndex = rIdx,
                    lazyIndex = lazyIdx,
                    odd = ri % 2 == 1,
                )
                val pos = ri + 1
                cutLines.firstOrNull { it.afterRank == pos && pos < group.rows.size }?.let { cut ->
                    entries += TableLazyEntry.Cut("cut:$gi:$pos", cut.label)
                }
            }
        }
        entries to rowToLazyIndex
    }
    val entries = builtEntries.first
    val rowToLazyIndex = builtEntries.second
    val totalRows = rowToLazyIndex.size

    val preferredInitialRow = remember(entries, markedIds) {
        entries.filterIsInstance<TableLazyEntry.TeamRow>()
            .firstOrNull { it.row.team.id in markedIds }
            ?.rowIndex ?: 0
    }

    LaunchedEffect(league.id, preferredInitialRow) {
        listFocus.focusedRowIndex = preferredInitialRow
        val lazyIdx = rowToLazyIndex.getOrElse(preferredInitialRow) { 0 }
        if (lazyIdx > 3) {
            runCatching { listState.scrollToItem((lazyIdx - 3).coerceAtLeast(0)) }
        } else {
            runCatching { listState.scrollToItem(0) }
        }
    }

    LaunchedEffect(focusTrigger) {
        if (focusTrigger > 0 && totalRows > 0) {
            val targetRow = listFocus.focusedRowIndex.coerceIn(0, totalRows - 1)
            listFocus.focusRow(targetRow, rowToLazyIndex[targetRow], totalRows)
        }
    }

    LaunchedEffect(refocusActiveTrigger) {
        if (refocusActiveTrigger > 0 && totalRows > 0) {
            val targetRow = listFocus.focusedRowIndex.coerceIn(0, totalRows - 1)
            listFocus.focusRow(targetRow, rowToLazyIndex[targetRow], totalRows)
        }
    }

    Column(
        modifier = modifier
            .clip(tokens.shapes.card)
            .background(tokens.colors.surfaceCard)
            .smoothBorder(tokens.borders.hairline, tokens.colors.borderSubtle, tokens.shapes.card)
            .padding(vertical = tokens.spacing.controlGap),
    ) {
        SportsTableHeaderRow(
            kind = table.kind,
            columns = table.columns,
            showForm = table.showForm,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onKeyEvent { event ->
                    listFocus.handleVerticalAndSideKeys(
                        event = event,
                        rowCount = totalRows,
                        lazyIndexOfRow = { r -> rowToLazyIndex.getOrElse(r) { 0 } },
                        onUpFromFirst = onUpFromFirstRow,
                        onRight = onRightToSideCard,
                    )
                },
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            entries.forEach { item ->
                when (item) {
                    is TableLazyEntry.GroupHeader -> {
                        item(key = item.key) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = tokens.colors.accent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                    is TableLazyEntry.Cut -> {
                        item(key = item.key) {
                            SportsPlayoffCutLine(item.label)
                        }
                    }
                    is TableLazyEntry.TeamRow -> {
                        val row = item.row
                        item(key = item.key) {
                            SportsTableRow(
                                tableKind = table.kind,
                                row = row,
                                columns = table.columns,
                                odd = item.odd,
                                followed = row.team.id in followedIds,
                                marked = row.team.id in markedIds,
                                showChange = !mergedView,
                                showForm = table.showForm,
                                focusRequester = listFocus.requester(item.rowIndex),
                                onFocused = {
                                    listFocus.focusedRowIndex = item.rowIndex
                                    onFocusRow(row)
                                },
                                onClick = { onActivateRow(row) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SportsTableHeaderRow(
    kind: SportsTable.Kind,
    columns: List<SportsTable.Column>,
    showForm: Boolean,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 14.dp, top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(RankColumnWidth))
        Text(
            text = if (kind == SportsTable.Kind.RACING) "DRIVER" else "TEAM",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = tokens.colors.textMuted,
            modifier = Modifier.weight(1f).padding(start = 30.dp),
        )
        columns.forEach { column ->
            Text(
                text = column.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = tokens.colors.textMuted,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(columnWidthFor(column)),
            )
        }
        if (showForm) {
            Text(
                text = "LAST 5",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = tokens.colors.textMuted,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(FormColumnWidth),
            )
        }
    }
}

@Composable
private fun SportsTableRow(
    tableKind: SportsTable.Kind,
    row: Standing,
    columns: List<SportsTable.Column>,
    odd: Boolean,
    followed: Boolean,
    marked: Boolean,
    showChange: Boolean,
    showForm: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val interaction = rememberSportsInteraction(baseScale = 1.015f, label = "sportsTableRowZoom")
    val teamColor = row.team.color?.let(::Color)
    val rowShape = RoundedCornerShape(6.dp)
    val zoneColor = row.noteColor?.let(::Color)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .sportsInteractive(
                state = interaction,
                focusRequester = focusRequester,
                onFocused = onFocused,
                onClick = onClick,
            )
            .clip(rowShape)
            .background(
                when {
                    interaction.highlighted -> tokens.colors.focusBackground
                    odd -> tokens.colors.surfaceElevated.copy(alpha = tokens.opacity.subtle)
                    else -> Color.Transparent
                },
            )
            .then(
                when {
                    marked && teamColor != null -> Modifier.background(
                        Brush.horizontalGradient(
                            0f to teamColor.copy(alpha = 0.28f),
                            0.55f to teamColor.copy(alpha = 0.08f),
                            1f to Color.Transparent,
                        ),
                    )
                    marked -> Modifier.background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFFFCE54).copy(alpha = 0.16f), Color.Transparent),
                        ),
                    )
                    else -> Modifier
                },
            )
            .then(
                when {
                    interaction.focused -> Modifier.smoothBorder(2.dp, tokens.colors.borderFocus, rowShape)
                    marked -> Modifier.smoothBorder(tokens.borders.hairline, Color(0xFFFFCE54).copy(alpha = 0.65f), rowShape)
                    else -> Modifier
                },
            )
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(zoneColor ?: Color.Transparent),
        )
        Row(
            modifier = Modifier.width(RankColumnWidth),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (row.rank > 0) "${row.rank}" else "",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = tokens.colors.textSecondary,
            )
            if (showChange && row.rankChange != null && row.rankChange != 0) {
                val up = row.rankChange > 0
                Text(
                    text = if (up) "▲" else "▼",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (up) Color(0xFF2E9E5B) else Color(0xFFE05A4F),
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }

        TableTeamCrest(team = row.team, size = 22.dp)
        Spacer(Modifier.width(8.dp))

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.team.shortName.ifBlank { row.team.name },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (marked || followed || interaction.focused) FontWeight.SemiBold else FontWeight.Medium,
                color = tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (followed) {
                Text(
                    text = " ★",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFF5C400),
                )
            }
            row.clincher?.let { cl ->
                Text(
                    text = " $cl",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (cl.equals("e", ignoreCase = true)) Color(0xFFE05A4F) else Color(0xFF5FD38D),
                )
            }
        }

        columns.forEach { column ->
            val value = SportsTable.cellValue(row, column) ?: SportsTable.MISSING
            val isPrimary = column.label == "PTS" ||
                (tableKind == SportsTable.Kind.RECORD && column.label == "PCT")
            val streakColor = if (column.label == "STRK") {
                when {
                    value.startsWith("W", ignoreCase = true) -> Color(0xFF5FD38D)
                    value.startsWith("L", ignoreCase = true) -> Color(0xFFE05A4F)
                    else -> null
                }
            } else {
                null
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isPrimary || streakColor != null) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    value == SportsTable.MISSING -> tokens.colors.textMuted
                    streakColor != null -> streakColor
                    isPrimary -> tokens.colors.textPrimary
                    else -> tokens.colors.textSecondary
                },
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(columnWidthFor(column)),
            )
        }

        if (showForm) {
            Row(
                modifier = Modifier.width(FormColumnWidth).padding(start = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.form.orEmpty().forEach { ch ->
                    FormBadge(ch)
                }
            }
        }
    }
}

@Composable
private fun TableTeamCrest(team: Team, size: Dp) {
    val tokens = MaterialTheme.nuvio
    val logo = team.logo?.takeIf(String::isNotBlank)
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(4.dp))
                .background(team.color?.let(::Color) ?: tokens.colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            if (logo == null) {
                Text(
                    text = team.abbreviation.take(3),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
        logo?.let {
            NuvioAsyncImage(
                model = it,
                contentDescription = null,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun FormBadge(result: Char) {
    val bg = when (result.uppercaseChar()) {
        'W' -> Color(0xFF2E9E5B)
        'L' -> Color(0xFFC0392B)
        else -> Color(0xFF5A5F6E)
    }
    Box(
        modifier = Modifier
            .size(11.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(bg),
    )
}

@Composable
private fun SportsPlayoffCutLine(label: String) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.cardPaddingCompact, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(tokens.colors.accent.copy(alpha = 0.55f)))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = tokens.colors.accent,
        )
        Box(Modifier.weight(1f).height(1.dp).background(tokens.colors.accent.copy(alpha = 0.55f)))
    }
}

@Composable
private fun SportsTeamStandingCard(
    table: SportsTable.TableData,
    row: Standing,
    followed: Boolean,
    settings: SportsSettings,
    recentGames: List<SportsEvent>,
    bandRequester: FocusRequester,
    onUp: () -> Boolean,
    onLeftToTable: () -> Boolean,
    onToggleFollow: () -> Unit,
    onOpenGame: (String) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val firstActionFocus = remember { FocusRequester() }
    val activeOrNextGame = remember(recentGames, row.team.id) {
        val teamGames = recentGames.filter {
            it.away.team.id == row.team.id || it.home.team.id == row.team.id
        }
        teamGames.firstOrNull { it.state == GameState.LIVE }
            ?: teamGames.filter { it.state == GameState.PRE }.minByOrNull { it.startMs }
            ?: teamGames.filter { it.state == GameState.POST }.maxByOrNull { it.startMs }
    }
    val summaryText = remember(table, row) { SportsTable.summaryLine(table, row) }
    val place = remember(table, row.team.id, row.rank) {
        table.placeText(row.team.id) ?: row.rank.takeIf { it > 0 }?.let(SportsTable::ordinal)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shapes.card)
            .background(tokens.colors.surfaceCard)
            .smoothBorder(tokens.borders.hairline, tokens.colors.borderSubtle, tokens.shapes.card)
            .padding(tokens.spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TableTeamCrest(team = row.team, size = 36.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.team.name + (if (followed) " ★" else ""),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (place != null) {
                    Text(
                        text = place,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.colors.textMuted,
                    )
                }
            }
        }

        if (summaryText.isNotBlank()) {
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textSecondary,
            )
        }

        val splits = listOfNotNull(
            row.homeRecord?.let { "Home $it" },
            row.awayRecord?.let { "Away $it" },
        ).joinToString(" · ")
        if (splits.isNotBlank()) {
            Text(
                text = splits,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textMuted,
            )
        }

        activeOrNextGame?.let { game ->
            val isAway = game.away.team.id == row.team.id
            val opponent = if (isAway) game.home.team.shortName else game.away.team.shortName
            val prefix = if (isAway) "@" else "vs"
            val whenLabel = when (game.state) {
                GameState.LIVE -> "LIVE · ${game.status ?: "Now"}"
                GameState.PRE -> "Next · ${kickoffText(game.startMs)}"
                GameState.POST -> {
                    val score = if (!settings.hideScores && game.away.score != null && game.home.score != null) {
                        " (${game.away.score}–${game.home.score})"
                    } else {
                        ""
                    }
                    "Last · Final$score"
                }
            }
            Text(
                text = "$prefix $opponent · $whenLabel",
                style = MaterialTheme.typography.bodySmall,
                color = if (game.isLive) tokens.colors.danger else tokens.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .sportsHorizontalBand(
                    bandRequester = bandRequester,
                    fallbackRequester = firstActionFocus,
                    onUp = onUp,
                    onDown = { true },
                )
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var attachedFirst = false
            fun attachFirst(): FocusRequester? = if (!attachedFirst) {
                attachedFirst = true
                firstActionFocus
            } else {
                null
            }

            if (!row.athlete && row.team.id.isNotBlank()) {
                val isFirst = !attachedFirst
                SportsSmallPill(
                    label = if (followed) "★ Following" else "☆ Follow",
                    selected = followed,
                    focusRequester = attachFirst(),
                    onLeft = if (isFirst) onLeftToTable else null,
                    onClick = onToggleFollow,
                )
            }
            activeOrNextGame?.let { game ->
                val watchOptions = remember(game.id, settings.picks, settings.blocked) {
                    SportsWatch.optionsFor(game, settings)
                }
                if (game.isLive && watchOptions.isNotEmpty()) {
                    val isFirst = !attachedFirst
                    SportsSmallPill(
                        label = "▶ Watch",
                        selected = true,
                        focusRequester = attachFirst(),
                        onLeft = if (isFirst) onLeftToTable else null,
                        onClick = { SportsWatch.playBest(game, settings) },
                    )
                }
                val isFirst = !attachedFirst
                SportsSmallPill(
                    label = "Game info",
                    selected = false,
                    focusRequester = attachFirst(),
                    onLeft = if (isFirst) onLeftToTable else null,
                    onClick = { onOpenGame(game.id) },
                )
            }
        }
    }
}

@Composable
private fun SportsTableLegend(
    table: SportsTable.TableData,
    cutLines: List<SportsTable.CutLine>,
    compact: Boolean = false,
) {
    val tokens = MaterialTheme.nuvio
    val zones = table.zones
    val clinchers = table.clinchers
    if (zones.isEmpty() && clinchers.isEmpty() && cutLines.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shapes.card)
            .background(tokens.colors.surfaceCard)
            .smoothBorder(tokens.borders.hairline, tokens.colors.borderSubtle, tokens.shapes.card)
            .padding(if (compact) tokens.spacing.cardPaddingCompact else tokens.spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        zones.forEach { zone ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .size(width = 4.dp, height = 12.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(zone.color)),
                )
                Text(
                    text = zone.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        clinchers.forEach { (code, meaning) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (code.equals("e", ignoreCase = true)) Color(0xFFE05A4F) else Color(0xFF5FD38D),
                    modifier = Modifier.width(16.dp),
                )
                Text(
                    text = meaning,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (cutLines.isNotEmpty()) {
            Text(
                text = cutLines.joinToString(" · ") { "${it.label} after ${SportsTable.ordinal(it.afterRank)}" },
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textMuted,
            )
        }
    }
}

@Composable
internal fun SportsSmallPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    onLeft: (() -> Boolean)? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val interaction = rememberSportsInteraction(baseScale = 1.05f, label = "sportsSmallPillZoom")
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .sportsInteractive(
                state = interaction,
                focusRequester = focusRequester,
                onExtraKey = if (onLeft != null) {
                    { ev -> arrowFocusDirection(ev) == FocusDirection.Left && onLeft() }
                } else {
                    null
                },
                onClick = onClick,
            )
            .clip(shape)
            .background(
                when {
                    selected -> tokens.colors.accentFill
                    interaction.highlighted -> SolidColor(tokens.colors.focusBackground)
                    else -> SolidColor(tokens.colors.surfaceCard)
                },
            )
            .smoothBorder(
                width = if (interaction.focused) 2.dp else tokens.borders.hairline,
                color = when {
                    interaction.focused -> tokens.colors.borderFocus
                    selected -> tokens.colors.borderSelected
                    else -> tokens.colors.borderSubtle
                },
                shape = shape,
            )
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected || interaction.focused) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) tokens.colors.onAccent else tokens.colors.textPrimary,
            maxLines = 1,
        )
    }
}

private fun columnWidthFor(column: SportsTable.Column): Dp = when (column.label) {
    "P", "GP", "W", "D", "T", "L", "OTL" -> 38.dp
    "BEHIND" -> 62.dp
    "PCT", "STRK" -> 50.dp
    else -> 46.dp
}

/** Sports whose feed publishes no standings table at all. (Racing has F1/NASCAR driver tables.) */
internal val NO_TABLE_SPORTS = setOf(
    Sport.GOLF, Sport.DARTS, Sport.SNOOKER, Sport.FIGHTING, Sport.WRESTLING, Sport.TENNIS,
)

private val RankColumnWidth = 36.dp
private val FormColumnWidth = 74.dp

@Composable
private fun BoxedNotice(title: String, body: String) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = Modifier.fillMaxWidth().padding(tokens.spacing.sectionGap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted)
    }
}
