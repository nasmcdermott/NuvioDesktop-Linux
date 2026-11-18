package com.nuvio.app.features.sports

/**
 * The "Steelers · 2nd in AFC North / 8 games ahead" rows under My Sports.
 *
 * Pure functions over the settings, the resolved team lists, the standings and each league's games,
 * so the rules can be tested without a screen, a feed or a store.
 */

/** One followed team's ahead-of-today section: who they are, where they sit, what is coming. */
internal data class SportsTeamAhead(
    val teamKey: String,
    val leagueId: String,
    /** Resolved team for the crest and name; null until the league's team list has loaded. */
    val team: Team?,
    /** "2nd in AFC North", when the league table says. */
    val note: String?,
    /** This team's not-yet-started games from now on, soonest first. */
    val games: List<SportsEvent>,
)

/**
 * Applies the channel-availability preference without hiding a followed team's games. Matches are
 * keyed by the unique feed event id; channel lookups are completed before this filter is applied.
 */
internal fun filterGamesByChannelAvailability(
    games: List<SportsEvent>,
    settings: SportsSettings,
    matchedEventIds: Set<String>,
): List<SportsEvent> {
    if (!settings.onlyGamesOnMyChannels) return games
    return games.filter { event ->
        event.id in matchedEventIds || SportsFollows.isFollowedTeamGame(settings, event)
    }
}

/** "1st", "2nd", "3rd", "21st" — the way standing positions are spoken. */
internal fun ordinal(n: Int): String {
    val hundred = n % 100
    if (hundred in 11..13) return "${n}th"
    return when (n % 10) {
        1 -> "${n}st"
        2 -> "${n}nd"
        3 -> "${n}rd"
        else -> "${n}th"
    }
}

/**
 * The short note for a standing row: the provider's own wording when it reads like a position
 * ("1st in AFC East"), else composed from the row's rank and group ("2nd in AFC North").
 */
internal fun standingsNote(row: Standing): String? {
    row.note?.takeIf { it.contains(" in ", ignoreCase = true) || it.contains(" place", ignoreCase = true) }
        ?.let { return it }
    val group = row.group?.takeIf { it.isNotBlank() } ?: return null
    if (row.rank <= 0) return null
    return "${ordinal(row.rank)} in $group"
}

/**
 * One [SportsTeamAhead] per followed team, in the order the user picked them.
 *
 * Games come from the selected day's scoreboard plus the league's ahead-of-today fetch, kept to
 * this team's sides that have not started yet, deduplicated and ordered soonest first.
 */
internal fun buildTeamAheadSections(
    settings: SportsSettings,
    teamsByLeague: Map<String, List<Team>>,
    notesByTeamKey: Map<String, String>,
    gamesByLeague: Map<String, List<SportsEvent>>,
    nowMs: Long,
): List<SportsTeamAhead> = settings.followedTeams.map { teamKey ->
    val leagueId = SportsFollows.leagueOfTeamKey(teamKey) ?: return@map SportsTeamAhead(teamKey, "", null, null, emptyList())
    val teamId = teamKey.substringAfter(':', "")
    val leagueGames = gamesByLeague[leagueId].orEmpty()
    val team = teamsByLeague[leagueId]?.firstOrNull { it.id == teamId }
        ?: leagueGames.asSequence()
            .flatMap { listOf(it.away.team, it.home.team) }
            .firstOrNull { it.id == teamId }
    val games = leagueGames
        .filter { event ->
            event.state == GameState.PRE &&
                event.startMs > nowMs &&
                (event.away.team.id == teamId || event.home.team.id == teamId)
        }
        .distinctBy { it.id }
        .sortedBy { it.startMs }
    SportsTeamAhead(
        teamKey = teamKey,
        leagueId = leagueId,
        team = team,
        note = notesByTeamKey[teamKey],
        games = games,
    )
}
