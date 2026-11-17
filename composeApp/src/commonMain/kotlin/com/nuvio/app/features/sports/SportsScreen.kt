package com.nuvio.app.features.sports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.keyLayer
import com.nuvio.app.core.ui.navigationKey
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.requestFocusLogged
import java.time.LocalDate

/**
 * The Sports tab.
 *
 * Its own top-level page, a sibling of Live TV rather than a part of it: it reads the user's
 * playlists and guide, but owns its own navigation, its own state and its own refresh.
 *
 * The day and sport are remembered across tab switches, because coming back to the page and losing
 * your place is the thing that makes a browse surface feel like a form.
 */
@Composable
internal fun SportsScreen(
    modifier: Modifier = Modifier,
    topChromePadding: androidx.compose.ui.unit.Dp? = null,
    onOpenSettings: () -> Unit = {},
) {
    val tokens = MaterialTheme.nuvio
    val days = remember { sportsDays(LocalDate.now()) }
    var selectedDay by rememberSaveable { mutableStateOf(days.firstOrNull { it.label == "Today" }?.day ?: days.getOrNull(3)?.day ?: "") }
    var selectedSport by rememberSaveable { mutableStateOf<String?>(null) }
    var openGameId by rememberSaveable { mutableStateOf<String?>(null) }
    val screenFocus = remember { FocusRequester() }

    val leagues = remember { SportsCatalog.leaguesIn(null) }
    val sports = remember(leagues) { SportsCatalog.sportsOf(leagues) }
    val sport = selectedSport?.let { name -> sports.firstOrNull { it.name == name } }
    val visibleLeagues = remember(leagues, sport) {
        if (sport == null) leagues else leagues.filter { it.sport == sport }
    }

    // Fetch the day for the leagues on screen. The repository coalesces and caches, so asking on
    // every recomposition is cheap and keeps the strip honest when the user flips between days.
    LaunchedEffect(selectedDay, visibleLeagues) {
        visibleLeagues.forEach { SportsRepository.refresh(it.id, selectedDay) }
    }

    val openGame = openGameId?.let { id ->
        visibleLeagues.asSequence()
            .mapNotNull { league ->
                val state = SportsRepository.dayState(league.id, selectedDay).value
                state.games.firstOrNull { it.id == id }?.let { it to league }
            }
            .firstOrNull()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .keyLayer("Sports") { screenFocus.requestFocusLogged() }
            .focusRequester(screenFocus)
            .onPreviewKeyEvent { event ->
                // navigationKey(), not key: a user who rebinds Back must still be able to leave a
                // game, and comparing the physical key would ignore their binding.
                if (event.type == KeyEventType.KeyDown &&
                    event.navigationKey() == Key.Escape &&
                    openGameId != null
                ) {
                    openGameId = null
                    true
                } else {
                    false
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // The floating bar overlays the top of the page; without this the title sits under it.
                .padding(top = topChromePadding ?: 24.dp)
                .padding(horizontal = tokens.spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sectionGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Sports",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = tokens.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                SportsSettingsLink(onClick = onOpenSettings)
            }

            SportsSportChips(
                sports = sports,
                selected = sport,
                onSelect = { selectedSport = it?.name },
            )

            SportsDateStrip(
                days = days,
                selected = selectedDay,
                onSelect = { selectedDay = it; openGameId = null },
            )

            if (openGame != null) {
                SportsGameDetail(
                    event = openGame.first,
                    league = openGame.second,
                    hideScores = false,
                    onBack = { openGameId = null },
                )
            } else {
                SportsDayList(
                    day = selectedDay,
                    leagues = visibleLeagues,
                    onOpenGame = { openGameId = it },
                )
            }
        }
    }
}

/** LIVE NOW above COMING UP, each grouped in the order the catalog lists its leagues. */
@Composable
private fun SportsDayList(
    day: String,
    leagues: List<League>,
    onOpenGame: (String) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val sections = collectSections(leagues, day)

    if (sections.live.isEmpty() && sections.upcoming.isEmpty() && !sections.anyLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No games on this date",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.colors.textPrimary,
                )
                Text(
                    text = if (sections.anyError) "Some leagues could not be reached." else "Try another day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textMuted,
                )
            }
        }
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sectionGap)) {
        if (sections.live.isNotEmpty()) {
            item {
                SportsSectionHeader("LIVE NOW", sections.live.size, live = true)
            }
            item {
                SportsGameRail(sections.live, leagues, onOpenGame)
            }
        }
        if (sections.upcoming.isNotEmpty()) {
            item {
                SportsSectionHeader("COMING UP", sections.upcoming.size, live = false)
            }
            item {
                SportsGameRail(sections.upcoming, leagues, onOpenGame)
            }
        }
    }
}

/** One horizontally scrolling rail of games; each card is its own focus stop. */
@Composable
private fun SportsGameRail(
    games: List<SportsEvent>,
    leagues: List<League>,
    onOpenGame: (String) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val byId = remember(leagues) { leagues.associateBy { it.id } }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.listGap),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        items(games, key = { "${it.leagueId}:${it.id}" }) { game ->
            SportsGameCard(
                event = game,
                league = byId[game.leagueId],
                hideScores = false,
                onClick = { onOpenGame(game.id) },
            )
        }
    }
}

/** What one day looks like once every league's state has been read. */
private data class DaySections(
    val live: List<SportsEvent>,
    val upcoming: List<SportsEvent>,
    val anyLoading: Boolean,
    val anyError: Boolean,
)

/**
 * Reads every league's cached state for [day] and splits it into the two sections.
 *
 * Finished games are left out: the page answers "what can I watch", and a game that ended this
 * morning is history the user did not ask for.
 */
@Composable
private fun collectSections(leagues: List<League>, day: String): DaySections {
    val states = leagues.map { league ->
        val state by remember(league.id, day) { SportsRepository.dayState(league.id, day) }.collectAsState()
        state
    }
    val allGames = states.flatMap { it.games }
    return DaySections(
        live = allGames.filter { it.state == GameState.LIVE }.sortedBy { it.startMs },
        upcoming = allGames.filter { it.state == GameState.PRE }.sortedBy { it.startMs },
        anyLoading = states.any { it.loading },
        anyError = states.any { it.error != null },
    )
}

/** The way to the Sports settings page, and to Live TV once the user needs playlists. */
@Composable
private fun SportsSettingsLink(onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    var focused by remember { mutableStateOf(false) }
    Text(
        text = "Settings",
        style = MaterialTheme.typography.labelLarge,
        color = if (focused) tokens.colors.accent else tokens.colors.textSecondary,
        modifier = Modifier
            .clip(tokens.shapes.chip)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(tokens.spacing.controlGap),
    )
}

/**
 * Whether the Sports entry belongs in the navigation.
 *
 * Two conditions, both cheap: the build carries the feature at all, and there is a playlist for it
 * to read. The second is the rule Live TV follows — a tab that cannot do anything yet is not shown,
 * rather than shown and then apologising. [SportsSettings.enabled] is the user's own switch.
 */
@Composable
internal fun rememberSportsTabVisible(): Boolean {
    if (!com.nuvio.app.core.build.AppFeaturePolicy.sportsEnabled) return false
    remember { com.nuvio.app.features.iptv.IptvRepository.ensureLoaded() }
    val iptv by com.nuvio.app.features.iptv.IptvRepository.settings.collectAsState()
    val sports by SportsSettingsRepository.settings.collectAsState()
    return sports.enabled && iptv.playlists.isNotEmpty()
}
