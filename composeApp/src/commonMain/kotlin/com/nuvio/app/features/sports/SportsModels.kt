package com.nuvio.app.features.sports

import kotlinx.serialization.Serializable

// fork:sports — the data spine of the Sports tab. Nothing here touches the network, the disk or
// Compose: SportsFeeds produces these, SportsCache stores them and the UI only reads them.

/**
 * The sports the Sports page groups by. Deliberately wider than v1 shows: [SportsCatalog] decides
 * which leagues exist, and the chip row is built from whatever sports those leagues cover.
 */
@Serializable
enum class Sport(val label: String) {
    AMERICAN_FOOTBALL("American Football"),
    BASKETBALL("Basketball"),
    HOCKEY("Hockey"),
    BASEBALL("Baseball"),
    FOOTBALL("Football"),
    RUGBY("Rugby"),
    FIGHTING("Fighting"),
    RACING("Racing"),
    GOLF("Golf"),
    TENNIS("Tennis"),
    CRICKET("Cricket"),
    DARTS("Darts"),
    SNOOKER("Snooker"),
    WRESTLING("Wrestling"),
    AUSSIE_RULES("Aussie Rules"),
    VOLLEYBALL("Volleyball"),
    LACROSSE("Lacrosse"),
    GAELIC("Gaelic Games"),
    NETBALL("Netball"),
    OTHER("More Sports"),
}

/**
 * A competition the Sports page can show: a league, a tournament, a racing series.
 *
 * [path] is the provider's own address segment (`football/nfl`, `soccer/eng.1`), so a second feed
 * implementation can translate it rather than the catalog knowing about any one provider.
 *
 * [guideWords] are what [SportsChannelMatcher] looks for in a channel's name when the guide has
 * nothing better; [minutes] is the usual running time, used to bound "is this game still live".
 */
@Serializable
data class League(
    val id: String,
    val sport: Sport,
    val name: String,
    val shortName: String,
    val path: String,
    /** Regions ("US", "UK", "IE", "CA", [ALL_REGIONS]) this league is offered in by default. */
    val regions: Set<String> = ALL_REGIONS,
    val guideWords: List<String> = emptyList(),
    val minutes: Int = 150,
    /** The league's brand colour, for the chip and card accents. */
    val color: Long = 0xFF3B4A63,
) {
    companion object {
        /** A league offered everywhere; the default for the ones no region should hide. */
        val ALL_REGIONS: Set<String> = setOf("US", "CA", "UK", "IE", "OTHER")
    }
}

/** A team, as the feed describes it. [logo] and [color] are optional: not every league sends them. */
@Serializable
data class Team(
    val id: String,
    val name: String,
    val shortName: String,
    val abbreviation: String,
    val logo: String? = null,
    val color: Long? = null,
)

/** Where a game is in its life. What the card shows and how often it is refreshed both key off this. */
@Serializable
enum class GameState { PRE, LIVE, POST }

/** One side of a game: the team and, once it has started, its score. */
@Serializable
data class GameSide(
    val team: Team,
    val score: String? = null,
    /** "12-4" or "3-1-2" — the provider's own wording, never reformatted here. */
    val record: String? = null,
    val winner: Boolean = false,
)

/**
 * A game, which is what the Sports page is a list of.
 *
 * [network] is the broadcast network the provider lists ("ESPN", "TNT SPORT 2"). It is the strongest
 * everyday clue for finding the game in the user's own channels, so it is kept even when the
 * provider sends no other presentation detail.
 */
@Serializable
data class SportsEvent(
    val id: String,
    val leagueId: String,
    /** Kick-off, epoch milliseconds. */
    val startMs: Long,
    val state: GameState,
    /** "3rd · 9:42", "FT", "7:30 PM" — the provider's own status text. */
    val status: String? = null,
    /**
     * The running clock on its own ("9:42"), when the provider sends one. [status] folds it
     * together with the period; this is here so the two can be drawn and sized separately.
     */
    val clock: String? = null,
    val away: GameSide,
    val home: GameSide,
    /** A short situational line ("ECU · Jayla Hearp makes free throws"), when the provider sends one. */
    val situation: String? = null,
    val network: String? = null,
    /** 0f..1f through the game, when it can be known; null for untimed sports. */
    val progress: Float? = null,
) {
    val isLive: Boolean get() = state == GameState.LIVE

    /** True when either side has a score to show, which is what "hide scores" suppresses. */
    val hasScore: Boolean get() = away.score != null || home.score != null
}

/** One line of a league table. [entries] are the provider's own column names, in its own order. */
@Serializable
data class Standing(
    val team: Team,
    /** e.g. "1st in AFC East". */
    val note: String? = null,
    val values: List<StandingValue> = emptyList(),
)

@Serializable
data class StandingValue(val label: String, val value: String)

/** One scoring play, for the detail view's "latest scoring" list. */
@Serializable
data class ScoringPlay(
    val period: String? = null,
    val clock: String? = null,
    val text: String,
    val awayScore: String? = null,
    val homeScore: String? = null,
)

/** One numbered statistic of a team ("Total Yards", "3rd down efficiency"): label plus display text. */
@Serializable
data class TeamStat(val label: String, val value: String, val category: String? = null)

/** A stat line for one player. [headline] is the provider's own summary of them. */
@Serializable
data class PlayerStat(
    val name: String,
    val position: String? = null,
    val headline: String? = null,
    val stats: List<StandingValue> = emptyList(),
)

/**
 * One stat category of a game — "Passing", "Rushing", "Turnovers". Each holds both teams' players,
 * because a box score reads across the two sides rather than down one.
 */
@Serializable
data class StatCategory(
    val name: String,
    val players: List<PlayerStat> = emptyList(),
)

/**
 * A game's detail payload. Fetched on demand, not with the scoreboard: most games are never opened,
 * and this is the expensive call.
 *
 * [teamStats] and [categories] are what a viewer actually reads after a game: touchdowns, turnovers,
 * possession, then who threw for what. Both are optional — the provider sends neither for every
 * league, and a widget that never gets them must still draw.
 */
@Serializable
data class GameSummary(
    val eventId: String,
    val scoring: List<ScoringPlay> = emptyList(),
    val standings: List<Standing> = emptyList(),
    val venue: String? = null,
    /** Both teams' comparison numbers, away first. */
    val teamStats: List<TeamStatPair> = emptyList(),
    /** Per-player categories (passing, rushing, receiving…), in the provider's order. */
    val categories: List<StatCategory> = emptyList(),
)

/**
 * One comparison row of the team-stat block: a label and both sides' values, so the UI can draw the
 * familiar away / label / home three columns without knowing which stat it is.
 */
@Serializable
data class TeamStatPair(
    val label: String,
    val away: String,
    val home: String,
    /** Higher is better for both sides, or null when the stat is neutral (turnovers can be either). */
    val awayLeads: Boolean = false,
    val homeLeads: Boolean = false,
)
