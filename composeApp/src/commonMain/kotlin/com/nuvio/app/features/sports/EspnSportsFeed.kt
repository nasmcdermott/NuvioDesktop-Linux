package com.nuvio.app.features.sports

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpGetText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The public, keyless scoreboard feed.
 *
 * Five addresses cover scores, schedules, teams, a game's detail and a league table:
 *
 *  - `site.api.espn.com/apis/site/v2/sports/{path}/scoreboard?dates=Ymd-Ymd&limit=1000`
 *  - `site.api.espn.com/apis/site/v2/sports/{path}/teams?limit=1000`
 *  - `site.api.espn.com/apis/site/v2/sports/{path}/summary?event={id}`
 *  - `site.api.espn.com/apis/v2/sports/{path}/standings?level=3`
 *
 * The payloads are large and loosely typed, and this is not an official API: every field is read
 * defensively and a missing one degrades the card rather than failing the league. A scoreboard that
 * cannot be parsed at all throws, so [SportsRepository] can show a failure instead of an empty day.
 */
internal class EspnSportsFeed(
    private val get: suspend (String) -> String = { httpGetText(it) },
) : SportsFeed {

    private val log = Logger.withTag("SportsFeed")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun scoreboard(league: League, fromDay: String, toDay: String): List<SportsEvent> {
        val dates = if (fromDay == toDay) fromDay else "$fromDay-$toDay"
        return scoreboardAt("${ESPN}${league.path}/scoreboard?dates=$dates&limit=1000", league)
    }

    override suspend fun scoreboardNow(league: League): List<SportsEvent> =
        scoreboardAt("${ESPN}${league.path}/scoreboard?limit=1000", league)

    private suspend fun scoreboardAt(url: String, league: League): List<SportsEvent> {
        val root = fetchObject(url) ?: return emptyList()
        val events = root["events"]?.asArray() ?: return emptyList()
        return events.mapNotNull { element ->
            runCatching { event(element.jsonObject, league) }
                .onFailure { log.w { "Skipped an event in ${league.id}: ${it::class.simpleName}: ${it.message}" } }
                .getOrNull()
        }
    }

    override suspend fun teams(league: League): List<Team> {
        val root = fetchObject("${ESPN}${league.path}/teams?limit=1000") ?: return emptyList()
        val container = root["sports"]?.asArray()?.firstOrNull()?.jsonObject ?: return emptyList()
        val entries = container["leagues"]?.asArray()?.firstOrNull()?.jsonObject
            ?.get("teams")?.asArray() ?: return emptyList()
        return entries.mapNotNull { element ->
            val inner = element.jsonObject["team"]?.jsonObject ?: return@mapNotNull null
            team(inner)
        }
    }

    override suspend fun summary(league: League, eventId: String): GameSummary {
        val root = fetchObject("${ESPN}${league.path}/summary?event=$eventId")
            ?: return GameSummary(eventId)
        val header = root["header"]?.jsonObject
        val boxscore = root["boxscore"]?.jsonObjectOrNull()
        return GameSummary(
            eventId = eventId,
            scoring = scoringPlays(root["scoringPlays"]?.asArray() ?: JsonArray(emptyList())),
            playByPlay = playByPlay(root),
            standings = standingsOf(header),
            venue = header?.get("competitions")?.asArray()?.firstOrNull()?.jsonObject
                ?.get("venue")?.jsonObject?.get("fullName")?.stringOrNull(),
            teamStats = teamStats(boxscore),
            categories = statCategories(boxscore),
        )
    }

    override suspend fun standings(league: League): List<Standing> {
        val url = standingsUrl(league)
        val root = fetchObject(url) ?: return emptyList()
        // Collect leaf tables along with their group name and optional parent conference name.
        data class Leaf(val name: String, val conference: String?, val entries: JsonArray)
        val leaves = mutableListOf<Leaf>()
        fun walk(node: JsonObject, parentName: String?) {
            val entries = node["standings"]?.jsonObjectOrNull()?.get("entries")?.asArray()
            val nodeName = node["name"]?.stringOrNull()
                ?: node["abbreviation"]?.stringOrNull()
            if (entries != null && entries.isNotEmpty()) {
                leaves += Leaf(nodeName ?: league.name, parentName, entries)
            }
            val children = node["children"]?.asArray() ?: return
            val nextParent = if (node === root) null else nodeName
            children.forEach { child ->
                child.jsonObjectOrNull()?.let { walk(it, nextParent) }
            }
        }
        walk(root, null)
        if (leaves.isEmpty()) return emptyList()

        // Racing endpoints list Drivers first, then Constructors; keep the Drivers championship.
        val firstEntry = leaves.first().entries.firstOrNull()?.jsonObjectOrNull()
        val isAthleteTable = firstEntry?.get("athlete")?.jsonObjectOrNull() != null &&
            firstEntry["team"]?.jsonObjectOrNull() == null
        val activeLeaves = if (isAthleteTable) leaves.take(1) else leaves

        val allRows = mutableListOf<Standing>()
        for (leaf in activeLeaves) {
            val parsed = leaf.entries.mapNotNull { entry ->
                runCatching { parseStandingEntry(entry.jsonObject, leaf.name, leaf.conference, league) }.getOrNull()
            }
            val ranked = when {
                parsed.isNotEmpty() && parsed.all { it.rank > 0 } -> parsed.sortedBy { it.rank }
                parsed.isNotEmpty() && parsed.all { it.seed != null } -> parsed.sortedBy { it.seed }
                else -> parsed
            }.mapIndexed { idx, row ->
                if (row.rank > 0) row else row.copy(rank = idx + 1)
            }

            if (isAthleteTable && ranked.isNotEmpty()) {
                val leadPts = ranked.first().values
                    .firstOrNull { it.key == "championshipPts" || it.key == "points" || it.label == "PTS" }
                    ?.value?.toDoubleOrNull()
                allRows += if (leadPts != null) {
                    ranked.map { row ->
                        val pts = row.values
                            .firstOrNull { it.key == "championshipPts" || it.key == "points" || it.label == "PTS" }
                            ?.value?.toDoubleOrNull()
                        val behind = if (pts == null || row.rank == 1) "–" else "-${SportsTable.formatDelta(leadPts - pts)}"
                        row.copy(values = row.values + StandingValue("BEHIND", behind, "behind"))
                    }
                } else {
                    ranked
                }
            } else {
                allRows += ranked
            }
        }
        return allRows
    }

    internal fun standingsUrl(league: League): String {
        val divisions = league.path in DIVISION_LEAGUES
        return "${ESPN_STANDINGS}${league.path}/standings" + if (divisions) "?level=3" else ""
    }

    private fun parseStandingEntry(
        obj: JsonObject,
        groupName: String,
        conferenceName: String?,
        league: League,
    ): Standing? {
        val teamObj = obj["team"]?.jsonObjectOrNull()
        val athleteObj = obj["athlete"]?.jsonObjectOrNull()
        val subject = teamObj ?: athleteObj ?: return null
        val statsArray = obj["stats"]?.asArray()
        val statsByName = HashMap<String, JsonObject>()
        var record: String? = null
        var homeRecord: String? = null
        var awayRecord: String? = null
        val values = ArrayList<StandingValue>()

        statsArray?.forEach { stat ->
            val statObj = stat.jsonObjectOrNull() ?: return@forEach
            val type = statObj["type"]?.stringOrNull()?.lowercase()
            when (type) {
                "total" -> record = statObj["summary"]?.stringOrNull() ?: statObj["displayValue"]?.stringOrNull()
                "home" -> homeRecord = statObj["summary"]?.stringOrNull() ?: statObj["displayValue"]?.stringOrNull()
                "road", "away" -> awayRecord = statObj["summary"]?.stringOrNull() ?: statObj["displayValue"]?.stringOrNull()
            }
            val rawName = statObj["name"]?.stringOrNull()
            if (rawName != null) statsByName[rawName] = statObj

            // abbreviation ("W", "L", "PCT", "GB") is the column heading a table wants; the raw
            // name ("winPercent") is camelCase API spelling, and displayName is too wide.
            val label = statObj["abbreviation"]?.stringOrNull()
                ?: statObj["shortDisplayName"]?.stringOrNull()
                ?: statObj["shortName"]?.stringOrNull()
                ?: rawName ?: return@forEach
            val displayValue = statObj["displayValue"]?.stringOrNull()?.takeIf { it.isNotBlank() }
                ?: return@forEach
            // Skip metadata-only stats from visible table columns unless they are the only ones.
            if (rawName in META_STAT_NAMES) return@forEach
            values += StandingValue(label = label, value = displayValue, key = rawName.orEmpty())
        }

        fun num(name: String): Double? {
            val s = statsByName[name] ?: return null
            return s["value"]?.let { runCatching { it.jsonPrimitive.contentOrNull?.toDoubleOrNull() }.getOrNull() }
                ?: s["displayValue"]?.stringOrNull()?.toDoubleOrNull()
        }
        fun text(name: String): String? =
            statsByName[name]?.get("displayValue")?.stringOrNull()?.trim()?.takeIf { it.isNotEmpty() }

        val noteObj = obj["note"]?.jsonObjectOrNull()
        val noteDesc = noteObj?.get("description")?.stringOrNull()
        val noteColor = noteObj?.get("color")?.stringOrNull()?.let(::parseHexColor)
            ?: noteDesc?.let(SportsTable::zoneColor)

        val clincher = text("clincher")?.takeIf { it != "*" && it.length <= 2 }
        val clincherText = statsByName["clincher"]?.get("description")?.stringOrNull()
        val rank = num("rank")?.toInt()?.takeIf { it > 0 } ?: 0
        val seed = num("playoffSeed")?.toInt()?.takeIf { it > 0 }
        val rankChange = num("rankChange")?.toInt()?.takeIf { it != 0 }

        val isPointsSport = athleteObj != null ||
            league.sport in setOf(Sport.FOOTBALL, Sport.RUGBY, Sport.HOCKEY, Sport.RACING, Sport.AUSSIE_RULES) ||
            ("points" in statsByName && "winPercent" !in statsByName)
        val sortValue = if (isPointsSport) {
            (num("points") ?: num("championshipPts") ?: 0.0) * 1000.0 + (num("pointDifferential") ?: num("differential") ?: 0.0)
        } else {
            (num("winPercent") ?: 0.0) * 1000.0 + (num("wins") ?: 0.0)
        }

        val parsedTeam = if (teamObj != null) {
            team(teamObj)
        } else {
            val name = subject["displayName"]?.stringOrNull() ?: subject["name"]?.stringOrNull().orEmpty()
            val shortName = subject["shortDisplayName"]?.stringOrNull() ?: subject["shortName"]?.stringOrNull() ?: name
            Team(
                id = subject["id"]?.stringOrNull().orEmpty(),
                name = name,
                shortName = shortName,
                abbreviation = subject["abbreviation"]?.stringOrNull()
                    ?: shortName.split(' ').lastOrNull()?.take(3)?.uppercase().orEmpty(),
                logo = subject["flag"]?.jsonObjectOrNull()?.get("href")?.stringOrNull()
                    ?: subject["headshot"]?.jsonObjectOrNull()?.get("href")?.stringOrNull(),
                color = league.color,
            )
        }

        return Standing(
            team = parsedTeam,
            note = noteDesc,
            values = values,
            rank = rank,
            group = groupName,
            conference = conferenceName,
            seed = seed,
            rankChange = rankChange,
            clincher = clincher,
            clincherText = clincherText,
            noteColor = noteColor,
            record = record,
            homeRecord = homeRecord,
            awayRecord = awayRecord,
            streak = text("streak"),
            sortValue = sortValue,
            athlete = athleteObj != null && teamObj == null,
        )
    }

    // region Parsing

    private fun event(obj: JsonObject, league: League): SportsEvent? {
        val id = obj["id"]?.stringOrNull() ?: return null
        val competition = obj["competitions"]?.asArray()?.firstOrNull()?.jsonObject ?: return null
        val competitors = competition["competitors"]?.asArray().orEmpty()
        val eventName = obj["shortName"]?.stringOrNull()?.takeIf { it.isNotBlank() }
            ?: obj["name"]?.stringOrNull()?.takeIf { it.isNotBlank() }

        val parsedSides = competitors.mapNotNull { element ->
            val side = element.jsonObjectOrNull() ?: return@mapNotNull null
            val teamObj = side["team"]?.jsonObjectOrNull()
            val athleteObj = side["athlete"]?.jsonObjectOrNull()
            val parsedTeam = when {
                teamObj != null -> team(teamObj)
                athleteObj != null -> athleteAsTeam(athleteObj, league)
                else -> return@mapNotNull null
            }
            val homeAway = side["homeAway"]?.stringOrNull()
            homeAway to GameSide(
                team = parsedTeam,
                score = side["score"]?.stringOrNull()?.takeIf { it.isNotBlank() },
                record = side["records"]?.asArray()?.firstOrNull()?.jsonObjectOrNull()
                    ?.get("summary")?.stringOrNull(),
                winner = side["winner"]?.jsonPrimitive?.booleanOrNull == true,
                linescores = side["linescores"]?.asArray()?.mapNotNull { ls ->
                    ls.jsonObjectOrNull()?.get("displayValue")?.stringOrNull()
                        ?: ls.jsonObjectOrNull()?.get("value")?.jsonPrimitive?.intOrNull?.toString()
                }.orEmpty(),
            )
        }

        // Standard 2-sided matchup (team or 1v1 athlete), OR a field event (F1, NASCAR, PGA) with
        // many competitors or an event title.
        val isFieldEvent = league.sport in FIELD_EVENT_SPORTS && (competitors.size > 2 || (parsedSides.size < 2 && eventName != null))
        val (away, home) = when {
            isFieldEvent -> fieldEventSides(eventName ?: league.name, league, parsedSides.map { it.second })
            parsedSides.size >= 2 -> {
                val byRole = parsedSides.toMap()
                val awaySide = byRole["away"] ?: parsedSides.first().second
                val homeSide = byRole["home"] ?: parsedSides.last().second
                awaySide to homeSide
            }
            else -> return null
        }

        val status = competition["status"]?.jsonObject ?: obj["status"]?.jsonObject
        val typeObj = status?.get("type")?.jsonObject
        val completed = typeObj?.get("completed")?.jsonPrimitive?.booleanOrNull == true
        val stateName = typeObj?.get("state")?.stringOrNull()
        val clock = status?.get("displayClock")?.stringOrNull()
        val period = status?.get("period")?.jsonPrimitive?.intOrNull
        val broadcasts = extractBroadcasts(competition)
        return SportsEvent(
            id = id,
            leagueId = league.id,
            startMs = parseEpoch(obj["date"]?.stringOrNull()),
            state = when {
                completed || stateName.equals("post", ignoreCase = true) -> GameState.POST
                stateName.equals("in", ignoreCase = true) -> GameState.LIVE
                clock != null && period != null && period > 0 -> GameState.LIVE
                else -> GameState.PRE
            },
            status = typeObj?.get("shortDetail")?.stringOrNull()
                ?: typeObj?.get("detail")?.stringOrNull(),
            clock = clock?.takeIf { it.isNotBlank() && it != "0:00" },
            away = away,
            home = home,
            situation = competition["situation"]?.jsonObject
                ?.get("lastPlay")?.jsonObject?.get("text")?.stringOrNull(),
            venue = competition["venue"]?.jsonObject?.get("fullName")?.stringOrNull(),
            network = broadcasts.firstOrNull(),
            broadcasts = broadcasts,
            title = if (isFieldEvent || league.sport == Sport.FIGHTING) eventName else null,
            progress = progressOf(status),
        )
    }

    private fun athleteAsTeam(athleteObj: JsonObject, league: League): Team {
        val fullName = athleteObj["displayName"]?.stringOrNull()
            ?: athleteObj["fullName"]?.stringOrNull()
            ?: athleteObj["shortName"]?.stringOrNull().orEmpty()
        val shortName = athleteObj["shortName"]?.stringOrNull() ?: fullName
        val abbr = fullName.split(' ').lastOrNull()?.take(3)?.uppercase().orEmpty()
        return Team(
            id = athleteObj["id"]?.stringOrNull().orEmpty(),
            name = fullName,
            shortName = shortName,
            abbreviation = abbr.ifBlank { league.shortName.take(3) },
            logo = athleteObj["headshot"]?.jsonObjectOrNull()?.get("href")?.stringOrNull()
                ?: athleteObj["flag"]?.jsonObjectOrNull()?.get("href")?.stringOrNull(),
            color = league.color,
        )
    }

    private fun fieldEventSides(
        eventName: String,
        league: League,
        leaders: List<GameSide>,
    ): Pair<GameSide, GameSide> {
        val topOne = leaders.getOrNull(0)
        val topTwo = leaders.getOrNull(1)
        if (topOne != null && topTwo != null && (topOne.score != null || topOne.winner)) {
            return topOne to topTwo
        }
        return GameSide(
            team = Team(
                id = "${league.id}:event",
                name = eventName,
                shortName = eventName,
                abbreviation = league.shortName.take(4),
                color = league.color,
            ),
        ) to GameSide(
            team = Team(
                id = "${league.id}:series",
                name = league.name,
                shortName = league.name,
                abbreviation = league.shortName.take(4),
                color = league.color,
            ),
        )
    }

    private fun extractBroadcasts(competition: JsonObject): List<String> {
        val out = LinkedHashSet<String>()
        competition["broadcasts"]?.asArray()?.forEach { entry ->
            entry.jsonObjectOrNull()?.get("names")?.asArray()?.forEach { n ->
                n.stringOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
            }
        }
        competition["geoBroadcasts"]?.asArray()?.forEach { entry ->
            entry.jsonObjectOrNull()?.get("media")?.jsonObjectOrNull()?.get("shortName")
                ?.stringOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
        }
        competition["broadcast"]?.stringOrNull()?.split('/')?.forEach { part ->
            part.trim().takeIf { it.isNotEmpty() }?.let(out::add)
        }
        return out.toList()
    }

    /** How far through the game we are, from the period and the clock; null when untimed. */
    private fun progressOf(status: JsonObject?): Float? {
        val period = status?.get("period")?.jsonPrimitive?.intOrNull ?: return null
        val typeObj = status["type"]?.jsonObjectOrNull()
        val periods = typeObj?.get("detail")?.stringOrNull() ?: return null
        // Only trust it for timed, period-based sports; a "Final" or a clockless sport has no share.
        if (periods.isBlank()) return null
        return (period / 4f).coerceIn(0f, 1f)
    }

    private fun team(obj: JsonObject): Team = Team(
        id = obj["id"]?.stringOrNull().orEmpty(),
        name = obj["displayName"]?.stringOrNull() ?: obj["name"]?.stringOrNull().orEmpty(),
        shortName = obj["shortDisplayName"]?.stringOrNull() ?: obj["name"]?.stringOrNull().orEmpty(),
        abbreviation = obj["abbreviation"]?.stringOrNull().orEmpty(),
        logo = obj["logo"]?.stringOrNull()
            ?: obj["logos"]?.asArray()?.firstOrNull()?.jsonObject?.get("href")?.stringOrNull(),
        color = obj["color"]?.stringOrNull()?.let { parseHexColor(it) },
    )

    private fun playByPlay(root: JsonObject): List<PlayByPlayEntry> {
        val directPlays = root["plays"]?.asArray()
        val plays = if (directPlays != null) {
            directPlays
        } else {
            // NFL summaries nest each play under its drive; flatten drives in feed order.
            root["drives"]?.jsonObjectOrNull()?.let { drives ->
                val previous = drives["previous"]?.asArray().orEmpty()
                val current = listOfNotNull(drives["current"])
                (previous + current).flatMap { drive ->
                    drive.jsonObjectOrNull()?.get("plays")?.asArray().orEmpty()
                }
            }.orEmpty()
        }
        return plays.mapIndexedNotNull { index, element ->
            val obj = element.jsonObjectOrNull() ?: return@mapIndexedNotNull null
            val text = obj["text"]?.stringOrNull()?.takeIf { it.isNotBlank() }
                ?: obj["shortDescription"]?.stringOrNull()?.takeIf { it.isNotBlank() }
                ?: return@mapIndexedNotNull null
            PlayByPlayEntry(
                id = obj["id"]?.stringOrNull() ?: obj["sequenceNumber"]?.stringOrNull() ?: "play:$index",
                period = obj["period"]?.jsonObjectOrNull()?.get("displayValue")?.stringOrNull()
                    ?: obj["period"]?.jsonObjectOrNull()?.get("number")?.stringOrNull()?.let { "Period $it" },
                clock = obj["clock"]?.jsonObjectOrNull()?.get("displayValue")?.stringOrNull(),
                text = text,
                awayScore = obj["awayScore"]?.stringOrNull(),
                homeScore = obj["homeScore"]?.stringOrNull(),
                scoring = obj["scoringPlay"]?.jsonPrimitive?.booleanOrNull == true,
            )
        }.distinctBy { it.id }
    }

    private fun scoringPlays(plays: JsonArray): List<ScoringPlay> = plays.mapNotNull { element ->
        val obj = element.jsonObjectOrNull() ?: return@mapNotNull null
        val text = obj["text"]?.stringOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        ScoringPlay(
            period = obj["period"]?.jsonObjectOrNull()?.get("displayValue")?.stringOrNull(),
            clock = obj["clock"]?.jsonObjectOrNull()?.get("displayValue")?.stringOrNull(),
            text = text,
            awayScore = obj["awayScore"]?.stringOrNull(),
            homeScore = obj["homeScore"]?.stringOrNull(),
        )
    }

    private fun standingsOf(header: JsonObject?): List<Standing> {
        // The game page carries a compact table of just the two teams under `header.standings`.
        val entries = header?.get("standings")?.jsonObjectOrNull()?.get("entries")?.asArray()
            ?: return emptyList()
        return entries.mapNotNull { element ->
            val obj = element.jsonObjectOrNull() ?: return@mapNotNull null
            val teamObj = obj["team"]?.jsonObjectOrNull() ?: return@mapNotNull null
            Standing(
                team = team(teamObj),
                note = obj["note"]?.jsonObjectOrNull()?.get("description")?.stringOrNull(),
                values = obj["stats"]?.asArray()?.mapNotNull { stat ->
                    val statObj = stat.jsonObjectOrNull() ?: return@mapNotNull null
                    val label = statObj["shortName"]?.stringOrNull()
                        ?: statObj["name"]?.stringOrNull() ?: return@mapNotNull null
                    StandingValue(label, statObj["displayValue"]?.stringOrNull().orEmpty())
                }.orEmpty(),
            )
        }
    }

    /**
     * The team-stat comparison block.
     *
     * The provider sends `boxscore.teams` as two entries, each with a `statistics` array; the rows
     * are joined on the stat's `name` so both sides line up even when one is missing a stat the
     * other has. Order follows the away team's list, which is the reading order of a box score.
     */
    private fun teamStats(boxscore: JsonObject?): List<TeamStatPair> {
        val teams = boxscore?.get("teams")?.asArray() ?: return emptyList()
        if (teams.size < 2) return emptyList()
        fun statsOf(index: Int): Map<String, String> =
            teams.getOrNull(index)?.jsonObjectOrNull()
                ?.get("statistics")?.asArray()
                ?.mapNotNull { entry ->
                    val obj = entry.jsonObjectOrNull() ?: return@mapNotNull null
                    val name = obj["name"]?.stringOrNull() ?: obj["label"]?.stringOrNull() ?: return@mapNotNull null
                    name to (obj["displayValue"]?.stringOrNull() ?: return@mapNotNull null)
                }
                ?.toMap()
                .orEmpty()

        val away = statsOf(0)
        val home = statsOf(1)
        return away.map { (name, awayValue) ->
            val homeValue = home[name] ?: "—"
            TeamStatPair(
                label = name.replaceFirstChar { it.uppercase() },
                away = awayValue,
                home = homeValue,
                // Numeric comparisons only; a "3rd down efficiency" string cannot be ordered sensibly.
                awayLeads = numeric(awayValue) != null && numeric(homeValue) != null &&
                    numeric(awayValue)!! > numeric(homeValue)!!,
                homeLeads = numeric(awayValue) != null && numeric(homeValue) != null &&
                    numeric(homeValue)!! > numeric(awayValue)!!,
            )
        }
    }

    /** "45% (3/7)" and "312" parse; "—" and "10:32" do not, which is the intent. */
    private fun numeric(value: String): Double? =
        value.substringBefore('(').trim().removeSuffix("%").toDoubleOrNull()

    /** Per-player categories: "Passing", "Rushing", "Turnovers"… each with both sides' players. */
    private fun statCategories(boxscore: JsonObject?): List<StatCategory> {
        val players = boxscore?.get("players")?.asArray() ?: return emptyList()
        return players.mapNotNull { teamEntry ->
            val teamObj = teamEntry.jsonObjectOrNull() ?: return@mapNotNull null
            teamObj["statistics"]?.asArray()?.mapNotNull { statEntry ->
                val statObj = statEntry.jsonObjectOrNull() ?: return@mapNotNull null
                val name = statObj["name"]?.stringOrNull()
                    ?: statObj["type"]?.stringOrNull()
                    ?: return@mapNotNull null
                val rows = statObj["athletes"]?.asArray() ?: return@mapNotNull null
                val lines = rows.mapNotNull { athleteEntry ->
                    val athleteObj = athleteEntry.jsonObjectOrNull() ?: return@mapNotNull null
                    val athleteName = athleteObj["athlete"]?.jsonObjectOrNull()?.get("displayName")?.stringOrNull()
                        ?: return@mapNotNull null
                    PlayerStat(
                        name = athleteName,
                        position = athleteObj["athlete"]?.jsonObjectOrNull()?.get("position")
                            ?.jsonObjectOrNull()?.get("abbreviation")?.stringOrNull(),
                        headline = run {
                            val parts = athleteObj["stats"]?.asArray()?.map { element ->
                                element.stringOrNull().orEmpty()
                            } ?: emptyList()
                            parts.filter { it.isNotBlank() }.joinToString(" ").takeIf { it.isNotBlank() }
                        },
                        stats = athleteObj["stats"]?.asArray()?.map { element ->
                            StandingValue("", element.stringOrNull().orEmpty())
                        }.orEmpty(),
                    )
                }
                if (lines.isEmpty()) null else StatCategory(name.replaceFirstChar { it.uppercase() }, lines)
            }.orEmpty()
        }.flatten()
            // The same category arrives once per team; fold them so "Passing" holds both sides.
            .groupBy { it.name }
            .map { (name, group) -> StatCategory(name, group.flatMap { it.players }) }
    }

    private suspend fun fetchObject(url: String): JsonObject? = try {
        json.parseToJsonElement(get(url)).jsonObjectOrNull()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        log.w { "Sports feed request failed: $url (${e.message})" }
        throw SportsFeedException(url, e)
    }

    private fun parseEpoch(raw: String?): Long {
        val text = raw?.trim() ?: return 0L
        // The provider sends ISO-8601 UTC ("2026-11-17T20:45:00Z"). Parsed by hand rather than with
        // java.time so the model layer stays free of platform types.
        val match = IsoUtc.matchEntire(text) ?: return 0L
        val (year, month, day, hour, minute, second) = match.destructured
        // The seconds group is optional: "2026-11-18T00:30Z" has none, and destructuring gives ""
        // for an unmatched optional group. toIntOrNull("") would have thrown; 0 is the right answer.
        return epochMillis(
            year.toIntOrNull() ?: return 0L,
            month.toIntOrNull() ?: return 0L,
            day.toIntOrNull() ?: return 0L,
            hour.toIntOrNull() ?: return 0L,
            minute.toIntOrNull() ?: return 0L,
            second.toIntOrNull() ?: 0,
        )
    }

    // endregion

    private companion object {
        const val ESPN = "https://site.api.espn.com/apis/site/v2/sports/"
        const val ESPN_STANDINGS = "https://site.api.espn.com/apis/v2/sports/"

        val DIVISION_LEAGUES = setOf("football/nfl", "basketball/nba", "hockey/nhl", "baseball/mlb")
        val META_STAT_NAMES = setOf("rank", "rankChange", "clincher", "playoffSeed")
        val FIELD_EVENT_SPORTS = setOf(Sport.RACING, Sport.GOLF)

        val IsoUtc = Regex(
            """(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):?(\d{2})?Z?""",
        )
    }
}

/** A feed request that failed. Carries the URL so the UI can say which league, not just "error". */
internal class SportsFeedException(val url: String, cause: Throwable) :
    Exception("Sports feed request failed: $url", cause)

// region JSON helpers — small and total, so a missing or wrong-typed field never throws.

internal fun JsonElement?.asArray(): JsonArray? = (this as? JsonArray)

internal fun JsonElement?.jsonObjectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement?.stringOrNull(): String? = this?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }

internal fun parseHexColor(hex: String): Long? {
    val clean = hex.trim().trimStart('#')
    if (clean.length != 6 && clean.length != 8) return null
    val value = clean.toLongOrNull(16) ?: return null
    return if (clean.length == 6) 0xFF000000L or value else value
}

/**
 * Days since 1970-01-01 for a proleptic Gregorian date. Howard Hinnant's `days_from_civil`, kept
 * here so the model layer needs no platform clock.
 */
private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    val y = if (month <= 2) year - 1 else year
    val era = (if (y >= 0) y else y - 399) / 400
    val yearOfEra = y - era * 400
    val dayOfYear = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era * 146097L + dayOfEra - 719468L
}

internal fun epochMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long =
    (daysFromCivil(year, month, day) * 86_400L + hour * 3_600L + minute * 60L + second) * 1_000L

// endregion
