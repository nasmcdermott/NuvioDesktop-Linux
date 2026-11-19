package com.nuvio.app.features.sports

/**
 * The leagues the Sports page offers, as data.
 *
 * Scoped down on purpose from the ~114 the reference implementation carries: this is the set a v1
 * can hold honestly, and every field here is one the matching or the cards actually use. Extending
 * it is mechanical — add a [League] — so a league missed today is not an architectural problem.
 *
 * [League.path] follows one provider's address shape. That is not an accident: it is a data string
 * rather than a URL built in code, so a different feed can translate or ignore it.
 */
internal object SportsCatalog {

    private val US = setOf("US")
    private val NA = setOf("US", "CA")
    private val UK = setOf("UK", "IE")

    val leagues: List<League> = listOf(
        // ---- American football
        League(
            id = "football/nfl", sport = Sport.AMERICAN_FOOTBALL, name = "NFL", shortName = "NFL",
            path = "football/nfl", regions = NA, minutes = 195,
            guideWords = listOf("NFL", "Football"), color = 0xFF013369,
            logo = "https://a.espncdn.com/i/teamlogos/leagues/500/nfl.png",
        ),
        League(
            id = "football/college-football", sport = Sport.AMERICAN_FOOTBALL,
            name = "College Football", shortName = "NCAAF", path = "football/college-football",
            regions = NA, minutes = 210,
            guideWords = listOf("College Football", "NCAA Football", "NCAAF", "CFB"), color = 0xFF1E3A5F,
            logo = "https://a.espncdn.com/redesign/assets/img/icons/NCAASports_Football.png",
        ),
        // ---- Basketball
        League(
            id = "basketball/nba", sport = Sport.BASKETBALL, name = "NBA", shortName = "NBA",
            path = "basketball/nba", regions = NA, minutes = 150,
            guideWords = listOf("NBA"), color = 0xFF1D428A,
            logo = "https://a.espncdn.com/i/teamlogos/leagues/500/nba.png",
        ),
        League(
            id = "basketball/wnba", sport = Sport.BASKETBALL, name = "WNBA", shortName = "WNBA",
            path = "basketball/wnba", regions = NA, minutes = 120,
            guideWords = listOf("WNBA"), color = 0xFFC8102E,
            logo = "https://a.espncdn.com/i/teamlogos/leagues/500/wnba.png",
        ),
        League(
            id = "basketball/mens-college-basketball", sport = Sport.BASKETBALL,
            name = "Men's College Basketball", shortName = "NCAAM",
            path = "basketball/mens-college-basketball", regions = US, minutes = 125,
            guideWords = listOf("College Basketball", "NCAA Basketball", "NCAAM"), color = 0xFF2B4C7E,
            logo = "https://a.espncdn.com/redesign/assets/img/icons/NCAASports_MensBasketball.png",
        ),
        // ---- Hockey and baseball
        League(
            id = "hockey/nhl", sport = Sport.HOCKEY, name = "NHL", shortName = "NHL",
            path = "hockey/nhl", regions = NA, minutes = 160,
            guideWords = listOf("NHL", "Hockey"), color = 0xFF111111,
            logo = "https://a.espncdn.com/i/teamlogos/leagues/500/nhl.png",
        ),
        League(
            id = "baseball/mlb", sport = Sport.BASEBALL, name = "MLB", shortName = "MLB",
            path = "baseball/mlb", regions = NA, minutes = 185,
            guideWords = listOf("MLB", "Baseball"), color = 0xFF002D72,
            logo = "https://a.espncdn.com/i/teamlogos/leagues/500/mlb.png",
        ),
        // ---- Football (soccer)
        League(
            id = "soccer/eng.1", sport = Sport.FOOTBALL, name = "Premier League", shortName = "EPL",
            path = "soccer/eng.1", minutes = 115,
            guideWords = listOf("Premier League", "EPL"), color = 0xFF3D195B,
            logo = "https://a.espncdn.com/i/leaguelogos/soccer/500/23.png",
        ),
        League(
            id = "soccer/eng.2", sport = Sport.FOOTBALL, name = "Championship", shortName = "EFL",
            path = "soccer/eng.2", regions = UK, minutes = 115,
            guideWords = listOf("Championship", "EFL"), color = 0xFF1C2C5B,
            logo = "https://a.espncdn.com/i/leaguelogos/soccer/500/24.png",
        ),
        League(
            id = "soccer/eng.fa", sport = Sport.FOOTBALL, name = "FA Cup", shortName = "FA CUP",
            path = "soccer/eng.fa", regions = UK, minutes = 125,
            guideWords = listOf("FA Cup"), color = 0xFF7A0019,
            logo = "https://a.espncdn.com/i/leaguelogos/soccer/500/40.png",
        ),
        League(
            id = "soccer/eng.league_cup", sport = Sport.FOOTBALL, name = "Carabao Cup", shortName = "EFL CUP",
            path = "soccer/eng.league_cup", regions = UK, minutes = 125,
            guideWords = listOf("Carabao Cup", "EFL Cup", "League Cup"), color = 0xFF00563F,
            logo = "https://a.espncdn.com/i/leaguelogos/soccer/500/41.png",
        ),
        League(
            id = "soccer/sco.1", sport = Sport.FOOTBALL, name = "Scottish Premiership", shortName = "SPFL",
            path = "soccer/sco.1", regions = UK, minutes = 115,
            guideWords = listOf("Scottish Premiership", "SPFL"), color = 0xFF2B2D7A,
            logo = "https://a.espncdn.com/i/leaguelogos/soccer/500/45.png",
        ),
        League(
            id = "soccer/uefa.champions", sport = Sport.FOOTBALL, name = "Champions League", shortName = "UCL",
            path = "soccer/uefa.champions", minutes = 120,
            guideWords = listOf("Champions League", "UCL"), color = 0xFF0B1E5B,
            logo = "https://a.espncdn.com/i/leaguelogos/soccer/500/2.png",
        ),
        League(
            id = "soccer/uefa.europa", sport = Sport.FOOTBALL, name = "Europa League", shortName = "UEL",
            path = "soccer/uefa.europa", minutes = 120,
            guideWords = listOf("Europa League", "UEL"), color = 0xFFF58220,
            logo = "https://a.espncdn.com/i/leaguelogos/soccer/500/2310.png",
        ),
        League(
            id = "soccer/esp.1", sport = Sport.FOOTBALL, name = "La Liga", shortName = "LALIGA",
            path = "soccer/esp.1", minutes = 115,
            guideWords = listOf("La Liga", "LaLiga"), color = 0xFFEE8707,
            logo = "https://a.espncdn.com/i/leaguelogos/soccer/500/15.png",
        ),
        League(
            id = "soccer/ita.1", sport = Sport.FOOTBALL, name = "Serie A", shortName = "SERIE A",
            path = "soccer/ita.1", minutes = 115,
            guideWords = listOf("Serie A"), color = 0xFF008FD7,
            logo = "https://a.espncdn.com/i/leaguelogos/soccer/500/12.png",
        ),
        League(
            id = "soccer/ger.1", sport = Sport.FOOTBALL, name = "Bundesliga", shortName = "BUND",
            path = "soccer/ger.1", minutes = 115,
            guideWords = listOf("Bundesliga"), color = 0xFFD20515,
            logo = "https://a.espncdn.com/i/leaguelogos/soccer/500/10.png",
        ),
        League(
            id = "soccer/fra.1", sport = Sport.FOOTBALL, name = "Ligue 1", shortName = "LIGUE 1",
            path = "soccer/fra.1", minutes = 115,
            guideWords = listOf("Ligue 1"), color = 0xFF091C3E,
            logo = "https://a.espncdn.com/i/leaguelogos/soccer/500/9.png",
        ),
        League(
            id = "soccer/usa.1", sport = Sport.FOOTBALL, name = "MLS", shortName = "MLS",
            path = "soccer/usa.1", regions = NA, minutes = 115,
            guideWords = listOf("MLS", "Major League Soccer"), color = 0xFF1B2A41,
            logo = "https://a.espncdn.com/i/leaguelogos/soccer/500/19.png",
        ),
        League(
            id = "soccer/mex.1", sport = Sport.FOOTBALL, name = "Liga MX", shortName = "LIGA MX",
            path = "soccer/mex.1", regions = NA, minutes = 115,
            guideWords = listOf("Liga MX"), color = 0xFF0C2340,
            logo = "https://a.espncdn.com/i/leaguelogos/soccer/500/22.png",
        ),
        // ---- Racing
        League(
            id = "racing/f1", sport = Sport.RACING, name = "Formula 1", shortName = "F1",
            path = "racing/f1", minutes = 120,
            guideWords = listOf("Formula 1", "F1"), color = 0xFFE10600,
            logo = "https://a.espncdn.com/combiner/i?img=/i/teamlogos/leagues/500/f1.png",
        ),
        League(
            id = "racing/nascar-premier", sport = Sport.RACING, name = "NASCAR Cup", shortName = "NASCAR",
            path = "racing/nascar-premier", regions = NA, minutes = 210,
            guideWords = listOf("NASCAR"), color = 0xFFE4002B,
            logo = "https://a.espncdn.com/combiner/i?img=/redesign/assets/img/icons/ESPN-icon-NASCAR.png",
        ),
        // ---- Golf
        League(
            id = "golf/pga", sport = Sport.GOLF, name = "PGA Tour", shortName = "PGA",
            path = "golf/pga", regions = NA, minutes = 240,
            guideWords = listOf("PGA", "Golf"), color = 0xFF004B87,
            logo = "https://a.espncdn.com/combiner/i?img=/i/teamlogos/leagues/500/pgatour.png",
        ),
        // ---- Tennis
        League(
            id = "tennis/atp", sport = Sport.TENNIS, name = "ATP Tour", shortName = "ATP",
            path = "tennis/atp", minutes = 150,
            guideWords = listOf("ATP", "Tennis"), color = 0xFF0A2E5C,
            logo = "https://a.espncdn.com/combiner/i?img=/redesign/assets/img/icons/ESPN-icon-tennis.png",
        ),
        // ---- Combat
        // Boxing has no working scoreboard path on this feed (every candidate answers HTTP 400), so it
        // is left out rather than shown as a league that is permanently broken. MMA covers fighting.
        League(
            id = "mma/ufc", sport = Sport.FIGHTING, name = "UFC", shortName = "UFC",
            path = "mma/ufc", minutes = 180,
            guideWords = listOf("UFC"), color = 0xFFD20A0A,
            logo = "https://a.espncdn.com/i/teamlogos/leagues/500/ufc.png",
        ),
        // ---- Cricket and rugby
        League(
            id = "cricket/8039", sport = Sport.CRICKET, name = "Cricket", shortName = "CRICKET",
            path = "cricket/8039", minutes = 240,
            guideWords = listOf("Cricket"), color = 0xFF007A33,
            logo = "https://a.espncdn.com/redesign/assets/img/icons/ESPN-icon-cricket.png",
        ),
        League(
            id = "rugby/180659", sport = Sport.RUGBY, name = "Rugby Union", shortName = "RUGBY",
            path = "rugby/180659", minutes = 110,
            guideWords = listOf("Rugby"), color = 0xFF0B6E4F,
            logo = "https://a.espncdn.com/combiner/i?img=/redesign/assets/img/icons/ESPN-icon-rugby.png",
        ),
    )

    private val byId: Map<String, League> = leagues.associateBy { it.id }

    fun league(id: String): League? = byId[id]

    /** The leagues offered in [region]; an unknown or empty region offers everything. */
    fun leaguesIn(region: String?): List<League> {
        val key = region?.trim()?.uppercase().orEmpty()
        if (key.isEmpty()) return leagues
        val matching = leagues.filter { key in it.regions }
        // A region nothing is tagged for must not leave the page empty; show everything instead.
        return matching.ifEmpty { leagues }
    }

    /** The sports that at least one of [leagues] covers, in the catalog's own order. */
    fun sportsOf(leagues: List<League>): List<Sport> =
        Sport.entries.filter { sport -> leagues.any { it.sport == sport } }

    /**
     * Words to look for in a channel's name for [league]'s games: its short name and every guide
     * word, lowercased, with the empties dropped.
     */
    fun searchWords(league: League): List<String> =
        (listOf(league.shortName) + league.guideWords)
            .map { it.lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
}
