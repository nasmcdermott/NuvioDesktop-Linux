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
        return events.mapNotNull { element -> runCatching { event(element.jsonObject, league) }.getOrNull() }
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
        return GameSummary(
            eventId = eventId,
            scoring = scoringPlays(root["scoringPlays"]?.asArray() ?: JsonArray(emptyList())),
            standings = standingsOf(header),
            venue = header?.get("competitions")?.asArray()?.firstOrNull()?.jsonObject
                ?.get("venue")?.jsonObject?.get("fullName")?.stringOrNull(),
        )
    }

    override suspend fun standings(league: League): List<Standing> {
        val url = "${ESPN_STANDINGS}${league.path}/standings?level=3"
        val root = fetchObject(url) ?: return emptyList()
        // The table hangs off `standings.entries`, sometimes nested under children that carry the
        // division names, so both shapes are walked.
        val entries = mutableListOf<Standing>()
        fun walk(node: JsonObject) {
            node["standings"]?.jsonObjectOrNull()?.get("entries")?.asArray()?.forEach { entry ->
                runCatching {
                    val obj = entry.jsonObject
                    val teamObj = obj["team"]?.jsonObject ?: return@runCatching
                    entries += Standing(
                        team = team(teamObj),
                        note = obj["note"]?.jsonObject?.get("description")?.stringOrNull(),
                        values = obj["stats"]?.asArray()?.mapNotNull { stat ->
                            val statObj = stat.jsonObjectOrNull() ?: return@mapNotNull null
                            val label = statObj["shortName"]?.stringOrNull()
                                ?: statObj["name"]?.stringOrNull() ?: return@mapNotNull null
                            StandingValue(label, statObj["displayValue"]?.stringOrNull().orEmpty())
                        }.orEmpty(),
                    )
                }
            }
            node["children"]?.asArray()?.forEach { child ->
                child.jsonObjectOrNull()?.let(::walk)
            }
        }
        root["standings"]?.jsonObjectOrNull()?.let(::walk)
        root["children"]?.asArray()?.forEach { child ->
            // Some leagues put the divisions at the top level instead.
            child.jsonObjectOrNull()?.let(::walk)
        }
        return entries
    }

    // region Parsing

    private fun event(obj: JsonObject, league: League): SportsEvent? {
        val id = obj["id"]?.stringOrNull() ?: return null
        val competition = obj["competitions"]?.asArray()?.firstOrNull()?.jsonObject
        val competitors = competition?.get("competitors")?.asArray().orEmpty()
        val sides = competitors.mapNotNull { element ->
            val side = element.jsonObject
            val teamObj = side["team"]?.jsonObject ?: return@mapNotNull null
            val homeAway = side["homeAway"]?.stringOrNull()
            homeAway to GameSide(
                team = team(teamObj),
                score = side["score"]?.stringOrNull()?.takeIf { it.isNotBlank() },
                record = side["records"]?.asArray()?.firstOrNull()?.jsonObject
                    ?.get("summary")?.stringOrNull(),
                winner = side["winner"]?.jsonPrimitive?.booleanOrNull == true,
            )
        }.toMap()
        val away = sides["away"] ?: sides.values.firstOrNull() ?: return null
        val home = sides["home"] ?: sides.values.lastOrNull() ?: return null

        val status = competition?.get("status")?.jsonObject ?: obj["status"]?.jsonObject
        val typeObj = status?.get("type")?.jsonObject
        val completed = typeObj?.get("completed")?.jsonPrimitive?.booleanOrNull == true
        val stateName = typeObj?.get("state")?.stringOrNull()
        val clock = status?.get("displayClock")?.stringOrNull()
        val period = status?.get("period")?.jsonPrimitive?.intOrNull
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
            away = away,
            home = home,
            situation = competition?.get("situation")?.jsonObject
                ?.get("lastPlay")?.jsonObject?.get("text")?.stringOrNull(),
            network = competition?.get("broadcasts")?.asArray()?.firstOrNull()?.jsonObject
                ?.get("names")?.asArray()?.firstOrNull()?.stringOrNull()
                ?: competition?.get("broadcast")?.stringOrNull(),
            progress = progressOf(status),
        )
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
        return epochMillis(
            year.toInt(), month.toInt(), day.toInt(),
            hour.toInt(), minute.toInt(), second.toInt(),
        )
    }

    // endregion

    private companion object {
        const val ESPN = "https://site.api.espn.com/apis/site/v2/sports/"
        const val ESPN_STANDINGS = "https://site.api.espn.com/apis/v2/sports/"

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
    val clean = hex.removePrefix("#").trim()
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
