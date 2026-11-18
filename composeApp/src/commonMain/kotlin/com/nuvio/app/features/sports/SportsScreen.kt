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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.nuvio.app.core.ui.arrowKeyFocusNavigation
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
    // null = All events; true = My Sports. Saveable so it survives a tab switch like the day does.
    var mySportsOnly by rememberSaveable { mutableStateOf(false) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val firstCardFocus = remember { FocusRequester() }
    var entryFocusRequests by remember { mutableIntStateOf(0) }
    // The card lives in a LazyColumn, which composes its first item after the frame this runs on, so
    // one attempt is refused. Retry briefly, and again whenever the day's contents change.
    LaunchedEffect(entryFocusRequests) {
        if (entryFocusRequests == 0) return@LaunchedEffect
        repeat(12) {
            if (firstCardFocus.requestFocusLogged()) return@LaunchedEffect
            kotlinx.coroutines.delay(48)
        }
    }
    val settings by SportsSettingsRepository.settings.collectAsState()

    // Offered once, on the first ever open with nothing followed. Afterwards an empty My Sports
    // shows a card instead, so a viewer who deliberately follows nothing is not asked each launch.
    LaunchedEffect(Unit) {
        if (!settings.setUp && SportsFollows.isEmpty(settings)) {
            pickerOpen = true
            SportsSettingsRepository.markSetUp()
        }
    }

    val leagues = remember { SportsCatalog.leaguesIn(settings.region.ifBlank { null }) }
    val sports = remember(leagues) { SportsCatalog.sportsOf(leagues) }
    val sport = selectedSport?.let { name -> sports.firstOrNull { it.name == name } }
    val visibleLeagues = remember(leagues, sport) {
        if (sport == null) leagues else leagues.filter { it.sport == sport }
    }

    // Fetch the day for the leagues on screen. The repository coalesces and caches, so asking on
    // every recomposition is cheap and keeps the strip honest when the user flips between days.
    //
    // It also has to repeat. The scoreboard carries a running clock, period and score, and a single
    // fetch per day froze all three the moment the page opened. Only today repeats: a finished day
    // cannot change, and re-asking it would spend requests on nothing.
    LaunchedEffect(selectedDay, visibleLeagues) {
        if (selectedDay != SportsRepository.today()) {
            visibleLeagues.forEach { SportsRepository.refresh(it.id, selectedDay) }
            return@LaunchedEffect
        }
        while (true) {
            visibleLeagues.forEach { SportsRepository.refresh(it.id, selectedDay) }
            kotlinx.coroutines.delay(SportsRepository.liveRefreshMs)
        }
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
            .keyLayer("Sports") { entryFocusRequests++; true }
            // Not focusable itself: a full-window focus stop has no neighbour in any direction, so
            // moveFocus always answered false and the arrows were dead on arrival. Focus belongs to
            // the chips and cards; this root only carries the requester and the arrow handling for
            // whatever inside it holds focus.
            .arrowKeyFocusNavigation(releaseWhenStuck = true)
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

            SportsPageChips(
                mySportsOnly = mySportsOnly,
                followedCount = SportsFollows.count(settings),
                sports = sports,
                selected = sport,
                onSelect = { selectedSport = it?.name },
                onToggleMySports = { mySportsOnly = !mySportsOnly; openGameId = null },
                onOpenPicker = { pickerOpen = true },
                // The cards are the only thing below the filters, so Down means the same to both.
                onDown = { entryFocusRequests++; true },
            )

            SportsDateStrip(
                days = days,
                selected = selectedDay,
                onSelect = { selectedDay = it; openGameId = null },
                // Down from the days means the games, the same as from the filters.
                onDown = { entryFocusRequests++; true },
            )

            if (openGame != null) {
                SportsGameDetail(
                    event = openGame.first,
                    league = openGame.second,
                    hideScores = settings.hideScores,
                    onBack = { openGameId = null },
                )
            } else {
                SportsDayList(
                    day = selectedDay,
                    leagues = visibleLeagues,
                    settings = settings,
                    mySportsOnly = mySportsOnly,
                    hideScores = settings.hideScores,
                    firstCardFocus = firstCardFocus,
                    onOpenGame = { openGameId = it },
                    onOpenPicker = { pickerOpen = true },
                )
            }
        }

        if (pickerOpen) {
            SportsFollowPicker(
                onDismiss = {
                    pickerOpen = false
                    // Dismissing counts as having been offered, so it never reopens by itself.
                    SportsSettingsRepository.markSetUp()
                },
            )
        }
    }
}

/**
 * The pill row: My Sports, then All events, then the sports.
 *
 * My Sports leads because it is the reason someone follows things; All events sits beside it so the
 * way back out is always one press away.
 */
@Composable
private fun SportsPageChips(
    mySportsOnly: Boolean,
    followedCount: Int,
    sports: List<Sport>,
    selected: Sport?,
    onSelect: (Sport?) -> Unit,
    onToggleMySports: () -> Unit,
    onOpenPicker: () -> Unit,
    onDown: () -> Boolean,
) {
    val tokens = MaterialTheme.nuvio
    // One LazyRow, not a Row of fixed chips beside one: a LazyRow placed in a Row has no weight, so
    // it took its full intrinsic width and pushed the leading chips off the left edge of the page.
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            // A horizontal scroller keeps Left and Right for itself and has no vertical neighbour,
            // so Down from the filters never reached the cards. That is the one case the shared
            // arrow helper cannot cover, because the key is answered inside this row.
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown && event.navigationKey() == Key.DirectionDown && onDown()
            },
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "my-sports") {
            SportsChip(
                label = if (followedCount == 0) "★ My Sports" else "★ My Sports ($followedCount)",
                selected = mySportsOnly,
                onClick = onToggleMySports,
            )
        }
        item(key = "all") {
            SportsChip(
                label = "All events",
                selected = !mySportsOnly,
                onClick = { if (mySportsOnly) onToggleMySports() },
            )
        }
        item(key = "follow") {
            SportsChip(label = "Follow…", selected = false, onClick = onOpenPicker)
        }
        items(sports, key = { it.name }) { sport ->
            SportsChip(
                label = sport.label,
                selected = sport == selected,
                onClick = { onSelect(if (sport == selected) null else sport) },
            )
        }
    }
}

/** LIVE NOW above COMING UP, each grouped in the order the catalog lists its leagues. */
@Composable
private fun SportsDayList(
    day: String,
    leagues: List<League>,
    settings: SportsSettings,
    mySportsOnly: Boolean,
    hideScores: Boolean,
    firstCardFocus: FocusRequester,
    onOpenGame: (String) -> Unit,
    onOpenPicker: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val sections = collectSections(leagues, day, settings, mySportsOnly)

    if (mySportsOnly && SportsFollows.isEmpty(settings)) {
        SportsFollowPrompt(onOpenPicker)
        return
    }

    if (sections.live.isEmpty() && sections.upcoming.isEmpty() && sections.results.isEmpty() && !sections.anyLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (mySportsOnly) "None of your teams play on this date" else "No games on this date",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.colors.textPrimary,
                )
                Text(
                    text = when {
                        sections.anyError -> "Some leagues could not be reached."
                        mySportsOnly -> "Try another day, or follow more teams."
                        else -> "Try another day."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textMuted,
                )
            }
        }
        return
    }

    // Decided once, in section order, rather than by a flag the item lambdas mutate: those run
    // later and in an order this code should not depend on.
    val firstSection = PubDayFocus(
        live = firstCardFocus.takeIf { sections.live.isNotEmpty() },
        upcoming = firstCardFocus.takeIf { sections.live.isEmpty() && sections.upcoming.isNotEmpty() },
        results = firstCardFocus.takeIf {
            sections.live.isEmpty() && sections.upcoming.isEmpty() && sections.results.isNotEmpty()
        },
    )
    LazyColumn(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sectionGap)) {
        if (sections.anyError) {
            // Without this a failed refresh looks exactly like a day with no games.
            item { SportsStaleNote(sections.oldestFetchMs) }
        }
        if (sections.live.isNotEmpty()) {
            item { SportsSectionHeader("LIVE NOW", sections.live.size, live = true) }
            item { SportsGameRail(sections.live, leagues, settings, hideScores, firstSection.live, onOpenGame) }
        }
        if (sections.upcoming.isNotEmpty()) {
            item { SportsSectionHeader("COMING UP", sections.upcoming.size, live = false) }
            item { SportsGameRail(sections.upcoming, leagues, settings, hideScores, firstSection.upcoming, onOpenGame) }
        }
        if (sections.results.isNotEmpty()) {
            item { SportsSectionHeader("RESULTS", sections.results.size, live = false) }
            item { SportsGameRail(sections.results, leagues, settings, hideScores, firstSection.results, onOpenGame) }
        }
    }
}

/**
 * "Could not refresh · showing 12 minutes ago".
 *
 * The feed is unofficial and will fail; saying so beside the games it failed to replace is the
 * difference between a stale page and a broken one.
 */
@Composable
private fun SportsStaleNote(fetchedAtMs: Long) {
    val tokens = MaterialTheme.nuvio
    val age = remember(fetchedAtMs) { relativeAge(fetchedAtMs) }
    Text(
        text = if (age == null) "Could not refresh" else "Could not refresh · showing $age",
        style = MaterialTheme.typography.labelSmall,
        color = tokens.colors.warning,
    )
}

/** "42 minutes ago", or null when nothing has ever been fetched. */
internal fun relativeAge(fetchedAtMs: Long, nowMs: Long = System.currentTimeMillis()): String? {
    if (fetchedAtMs <= 0L) return null
    val minutes = (nowMs - fetchedAtMs).coerceAtLeast(0L) / 60_000L
    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
        else -> {
            val hours = minutes / 60L
            if (hours == 1L) "1 hour ago" else "$hours hours ago"
        }
    }
}

/** An empty My Sports that explains itself and offers the one action that fixes it. */
@Composable
private fun SportsFollowPrompt(onOpenPicker: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
        ) {
            Text(
                text = "Nothing followed yet",
                style = MaterialTheme.typography.titleMedium,
                color = tokens.colors.textPrimary,
            )
            Text(
                text = "Follow teams and leagues to have their games lead this page.",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
            )
            SportsChip(label = "Follow teams and leagues", selected = true, onClick = onOpenPicker)
        }
    }
}

/** One horizontally scrolling rail of games; each card is its own focus stop. */
@Composable
private fun SportsGameRail(
    games: List<SportsEvent>,
    leagues: List<League>,
    settings: SportsSettings,
    hideScores: Boolean,
    entryFocus: FocusRequester?,
    onOpenGame: (String) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val byId = remember(leagues) { leagues.associateBy { it.id } }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.listGap),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        itemsIndexed(games, key = { _, game -> "${game.leagueId}:${game.id}" }) { index, game ->
            SportsGameCard(
                event = game,
                league = byId[game.leagueId],
                hideScores = hideScores,
                followed = SportsFollows.isFollowedTeamGame(settings, game),
                // Only the first card of the first rail takes it; the others pass null so a requester
                // is never attached to a card that has not been composed yet.
                focusRequester = entryFocus.takeIf { index == 0 },
                onClick = { onOpenGame(game.id) },
            )
        }
    }
}

/**
 * Down from a filter row: put focus on the day's first game. Taken as handled even when there is
 * none — a day with no games is a normal state, not an unhandled key, and letting it fall through
 * would move focus out of the page instead.
 */
/** Which rail takes the entry focus: the first section that has a game in it. */
private class PubDayFocus(
    val live: FocusRequester?,
    val upcoming: FocusRequester?,
    val results: FocusRequester?,
)

/** What one day looks like once every league's state has been read. */
private data class DaySections(
    val live: List<SportsEvent>,
    val upcoming: List<SportsEvent>,
    /** Finished games. A followed team's last result is the thing people open this page for. */
    val results: List<SportsEvent>,
    val anyLoading: Boolean,
    /** Set when any league failed; the page keeps showing whatever it already had. */
    val anyError: Boolean,
    /** The oldest fetch among the leagues shown, for the "showing X ago" line. */
    val oldestFetchMs: Long,
)

/**
 * Reads every league's cached state for [day] and splits it into three sections.
 *
 * Results are included rather than dropped: the page answers "what is on, and how did we do", and
 * for a followed team the second half of that is the reason to open it. They sort last so a live
 * game is never pushed off screen by one that finished this morning.
 */
@Composable
private fun collectSections(
    leagues: List<League>,
    day: String,
    settings: SportsSettings,
    mySportsOnly: Boolean,
): DaySections {
    val states = leagues.map { league ->
        val state by remember(league.id, day) { SportsRepository.dayState(league.id, day) }.collectAsState()
        state
    }
    val allGames = states.flatMap { it.games }
        .filter { !mySportsOnly || SportsFollows.isInMySports(settings, it) }
    val split = splitDay(allGames)
    val fetched = states.map { it.fetchedAtMs }.filter { it > 0L }
    return DaySections(
        live = split.live,
        upcoming = split.upcoming,
        results = split.results,
        anyLoading = states.any { it.loading },
        anyError = states.any { it.error != null },
        oldestFetchMs = fetched.minOrNull() ?: 0L,
    )
}

/** A day's games, split into the sections the page draws. Order within each is decided here. */
internal data class SplitDay(
    val live: List<SportsEvent>,
    val upcoming: List<SportsEvent>,
    val results: List<SportsEvent>,
) {
    val isEmpty: Boolean get() = live.isEmpty() && upcoming.isEmpty() && results.isEmpty()
}

/**
 * Live by kick-off, upcoming by kick-off, results most recent first.
 *
 * Results run backwards on purpose: the last result is the one someone opening the page wants,
 * whereas the next game is the one they want from the other two sections.
 */
internal fun splitDay(games: List<SportsEvent>): SplitDay = SplitDay(
    live = games.filter { it.state == GameState.LIVE }.sortedBy { it.startMs },
    upcoming = games.filter { it.state == GameState.PRE }.sortedBy { it.startMs },
    results = games.filter { it.state == GameState.POST }.sortedByDescending { it.startMs },
)

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
