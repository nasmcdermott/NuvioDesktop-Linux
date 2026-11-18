package com.nuvio.app.features.sports

/**
 * Following teams and leagues, and what "My Sports" then shows.
 *
 * Pure functions over [SportsSettings] and [SportsEvent], so the rules can be read and tested
 * without a screen, a feed or a store.
 *
 * Two rules the UI depends on:
 *  - A team is identified by its league as well as its id. Team ids are only unique within a league,
 *    and the same franchise number appears in more than one sport's feed.
 *  - Following a league is not the same as following its teams. A followed league shows every game
 *    in it; followed teams show only their own. Neither implies the other, because auto-following a
 *    league the moment one team is followed would fill My Sports with games nobody asked for.
 */
internal object SportsFollows {

    /** "leagueId:teamId" — the key a followed team is stored under. */
    fun teamKey(leagueId: String, teamId: String): String = "$leagueId:$teamId"

    /** The league a [teamKey] belongs to, or null when the key is malformed. */
    fun leagueOfTeamKey(teamKey: String): String? =
        teamKey.substringBefore(':', "").takeIf { it.isNotEmpty() && ':' in teamKey }

    fun isLeagueFollowed(settings: SportsSettings, leagueId: String): Boolean =
        leagueId in settings.followedLeagues

    fun isTeamFollowed(settings: SportsSettings, leagueId: String, teamId: String): Boolean =
        teamKey(leagueId, teamId) in settings.followedTeams

    /** True when [side] is one of the followed teams of [leagueId]. */
    fun isSideFollowed(settings: SportsSettings, leagueId: String, side: GameSide): Boolean =
        isTeamFollowed(settings, leagueId, side.team.id)

    /**
     * Whether [event] belongs in My Sports: its league is followed, or either side is.
     *
     * A followed league is enough on its own — that is what following a competition means — so this
     * is a disjunction rather than requiring a team match.
     */
    fun isInMySports(settings: SportsSettings, event: SportsEvent): Boolean {
        if (event.leagueId in settings.followedLeagues) return true
        return isSideFollowed(settings, event.leagueId, event.away) ||
            isSideFollowed(settings, event.leagueId, event.home)
    }

    /** Everything in [events] that My Sports shows, order preserved. */
    fun mySports(settings: SportsSettings, events: List<SportsEvent>): List<SportsEvent> =
        events.filter { isInMySports(settings, it) }

    /** True when nothing at all is followed, which is what the first-open picker keys off. */
    fun isEmpty(settings: SportsSettings): Boolean =
        settings.followedTeams.isEmpty() && settings.followedLeagues.isEmpty()

    /** How many things are followed, for the settings page's summary line. */
    fun count(settings: SportsSettings): Int =
        settings.followedTeams.size + settings.followedLeagues.size

    /**
     * Whether [event] is worth marking on a card as a followed team's game, so the page can show why
     * it is in My Sports. False for a game that is only there because its league is followed.
     */
    fun isFollowedTeamGame(settings: SportsSettings, event: SportsEvent): Boolean =
        isSideFollowed(settings, event.leagueId, event.away) ||
            isSideFollowed(settings, event.leagueId, event.home)
}
