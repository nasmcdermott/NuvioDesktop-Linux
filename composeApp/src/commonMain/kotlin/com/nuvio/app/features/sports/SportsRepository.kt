package com.nuvio.app.features.sports

import co.touchlab.kermit.Logger
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The Sports tab's data, cached first and refreshed behind.
 *
 * One state per league and day, so the date strip can move without refetching the league it is
 * already showing. A failed refresh keeps what was cached and records the failure next to it — the
 * feed is unofficial and will fail, and an empty screen is a worse answer than a slightly old one.
 */
internal object SportsRepository {
    private val log = Logger.withTag("Sports")
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /** How long a fetched day is trusted before a refresh is worth making. */
    /**
     * How often the Sports page re-asks for the day it is showing. The scoreboard carries a running
     * clock, period and score, so a page that fetches once shows a frozen game; this is the interval
     * the page's timer uses, and the repository's own cache TTL for a live day, so the timer's ask is
     * never served from the cache it just filled.
     */
    internal val liveRefreshMs: Long get() = Freshness.LIVE_MS

    internal object Freshness {
        // The feed's clock is not a ticker: ESPN rewrites displayClock only when the game clock
        // actually stops (a score, a timeout, a dead ball), which is roughly once a minute. Asking
        // more often than this buys nothing, and asking less often makes a live game look frozen.
        const val LIVE_MS = 30_000L
        const val FUTURE_MS = 30 * 60_000L
        const val PAST_MS = 24 * 60 * 60_000L
        const val TEAMS_MS = 7 * 24 * 60 * 60_000L
    }

    data class DayState(
        val leagueId: String,
        val day: String,
        val games: List<SportsEvent> = emptyList(),
        val loading: Boolean = false,
        /** Set when the last refresh failed; [games] may still hold the cached (older) day. */
        val error: String? = null,
        val fetchedAtMs: Long = 0L,
    ) {
        val isEmpty: Boolean get() = games.isEmpty()
    }

    private val states = java.util.concurrent.ConcurrentHashMap<String, MutableStateFlow<DayState>>()
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private val teamsCache = java.util.concurrent.ConcurrentHashMap<String, List<Team>>()

    private val _version = MutableStateFlow(0)

    /** Bumps when anything is fetched, so a screen showing several leagues can re-read once. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val dayFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    private fun stateKey(leagueId: String, day: String) = "$leagueId|$day"

    fun dayState(leagueId: String, day: String): StateFlow<DayState> =
        states.getOrPut(stateKey(leagueId, day)) {
            MutableStateFlow(DayState(leagueId = leagueId, day = day))
        }.asStateFlow()

    /** Today in the user's own zone, as `yyyymmdd`. */
    fun today(zone: ZoneId = ZoneId.systemDefault()): String = LocalDate.now(zone).format(dayFormatter)

    fun day(minusDays: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        LocalDate.now(zone).minusDays(minusDays).format(dayFormatter)

    /**
     * Makes [leagueId]'s games for [day] available: the disk cache first (shown at once), then a
     * download when the cache is missing or stale. [force] skips the freshness check.
     *
     * Concurrent calls for one league and day collapse into one, as [IptvRepository.refresh] does:
     * the date strip re-composing must not start a second download.
     */
    fun refresh(leagueId: String, day: String, force: Boolean = false) {
        val league = SportsCatalog.league(leagueId) ?: return
        val state = states.getOrPut(stateKey(leagueId, day)) {
            MutableStateFlow(DayState(leagueId = leagueId, day = day))
        }
        val lock = locks.getOrPut(stateKey(leagueId, day)) { Mutex() }
        if (!lock.tryLock()) return
        scope.launch {
            try {
                if (state.value.games.isEmpty()) {
                    SportsCache.readGames(leagueId, day, day)?.let { cached ->
                        state.update {
                            it.copy(games = cached.games, fetchedAtMs = cached.fetchedAtMs)
                        }
                        _version.update { it + 1 }
                    }
                }
                val now = System.currentTimeMillis()
                val age = now - state.value.fetchedAtMs
                if (!force && state.value.fetchedAtMs > 0L && age < ttlFor(day)) return@launch

                state.update { it.copy(loading = true, error = null) }
                try {
                    val games = SportsFeeds.current.scoreboard(league, day, day)
                    state.value = DayState(
                        leagueId = leagueId,
                        day = day,
                        games = games,
                        loading = false,
                        error = null,
                        fetchedAtMs = now,
                    )
                    SportsCache.writeGames(leagueId, day, day, games, now)
                    _version.update { it + 1 }
                    log.i { "Loaded ${league.name} $day: ${games.size} games" }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // Keep whatever is on screen; the error is shown beside it, not instead of it.
                    state.update { it.copy(loading = false, error = e.message ?: "Could not load games") }
                    log.w { "Sports refresh failed for ${league.id} $day: ${e.message}" }
                }
            } finally {
                lock.unlock()
            }
        }
    }

    /** [leagueId]'s teams, from memory, then disk, then the feed. For the follow picker. */
    fun teams(leagueId: String, onLoaded: (List<Team>) -> Unit = {}) {
        teamsCache[leagueId]?.let { cached ->
            onLoaded(cached)
            return
        }
        val league = SportsCatalog.league(leagueId) ?: return
        scope.launch {
            val cached = SportsCache.readTeams(leagueId)
            if (cached != null) {
                teamsCache[leagueId] = cached.teams
                onLoaded(cached.teams)
                if (System.currentTimeMillis() - cached.fetchedAtMs < Freshness.TEAMS_MS) return@launch
            }
            runCatching { SportsFeeds.current.teams(league) }
                .onSuccess { teams ->
                    teamsCache[leagueId] = teams
                    SportsCache.writeTeams(leagueId, teams, System.currentTimeMillis())
                    onLoaded(teams)
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    log.w { "Could not load ${league.name} teams: ${error.message}" }
                }
        }
    }

    /** One game's detail, fetched on open rather than with the scoreboard. */
    suspend fun summary(leagueId: String, eventId: String): GameSummary? {
        val league = SportsCatalog.league(leagueId) ?: return null
        return runCatching { SportsFeeds.current.summary(league, eventId) }
            .onFailure { error ->
                if (error is CancellationException) throw error
                log.w { "Could not load summary for $eventId: ${error.message}" }
            }
            .getOrNull()
    }

    /** A league's table, or null when it has none (most cup competitions do not). */
    suspend fun standings(leagueId: String): List<Standing>? {
        val league = SportsCatalog.league(leagueId) ?: return null
        return runCatching { SportsFeeds.current.standings(league) }
            .onFailure { error ->
                if (error is CancellationException) throw error
                log.w { "Could not load ${league.name} standings: ${error.message}" }
            }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    /** Drops every league's games in memory and on disk, for Settings > Clear sports cache. */
    fun clear() {
        states.values.forEach { state -> state.update { DayState(it.leagueId, it.day) } }
        SportsCache.clearGames()
        _version.update { it + 1 }
    }

    internal fun forgetAll() {
        states.clear()
        locks.clear()
        teamsCache.clear()
    }

    /**
     * How long [day]'s games stay fresh. A live game moves constantly, today's schedule barely at
     * all, and a day that has finished never changes again — so the intervals are far apart, and
     * the cheap ones do not hammer a feed that has no rate limit but also no obligation to us.
     */
    private fun ttlFor(day: String): Long {
        val today = today()
        return when {
            day > today -> Freshness.FUTURE_MS
            day < today -> Freshness.PAST_MS
            // Today is the only day that changes while it is being watched: the scoreboard carries a
            // running clock and a live score. A five-minute cache over it (which is what this was)
            // served the same frozen numbers for the whole first five minutes of every visit, so the
            // page looked dead even once the page started asking again on a timer.
            else -> Freshness.LIVE_MS
        }
    }
}
