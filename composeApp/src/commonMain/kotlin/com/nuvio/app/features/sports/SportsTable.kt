package com.nuvio.app.features.sports

import java.util.Locale
import kotlin.math.floor

/**
 * Turning a provider's standings into a table someone can read.
 *
 * The feed sends a different column set per sport (wins/losses/ties for football, points and
 * overtime losses for hockey, points and goal difference for soccer, championship points for
 * racing), and US leagues nest divisions inside conferences. This shapes raw [Standing] rows into
 * sport-specific columns, division/conference/league views, qualification zones, clincher keys and
 * playoff cut lines without touching the network or Compose.
 */
internal object SportsTable {

    /**
     * Fallback column preference order when rows come without canonical stat keys or from an
     * unfamiliar competition. Record first, then rate, then volume.
     */
    private val PREFERRED = listOf(
        "W", "L", "T", "OTL", "PCT",
        "GP", "P", "PTS", "DIFF", "GD",
        "PF", "PA", "GF", "GA", "GB", "STRK",
    )

    /** Wide enough for the usual five or six columns without horizontal scrolling. */
    const val MAX_COLUMNS = 6

    /** What stands in for a value the provider did not send. */
    const val MISSING = "—"

    /** One column of the table: the heading shown and optional canonical stat key (`"points"`). */
    data class Column(val label: String, val key: String = "")

    enum class Kind {
        /** Points tables (football/soccer, rugby, Aussie rules): P W D L GD PTS. */
        TABLE,
        /** US win-percentage leagues (NFL, NBA, WNBA, MLB, NCAA): W L [T] PCT GB STRK. */
        RECORD,
        /** Ice hockey: GP W L OTL PTS STRK. */
        HOCKEY,
        /** Drivers' championship (F1, NASCAR): PTS BEHIND. */
        RACING,
    }

    enum class View(val label: String) {
        DIVISION("Division"),
        CONFERENCE("Conference"),
        LEAGUE("League"),
    }

    /** Qualification / relegation band for the table legend. */
    data class Zone(val label: String, val color: Long)

    /** One division, conference or single-table block. */
    data class Group(
        val name: String,
        val conference: String?,
        val rows: List<Standing>,
    )

    /** Playoff / play-in cut line drawn after 1-based [afterRank]. */
    data class CutLine(val afterRank: Int, val label: String)

    /** A complete shaped league table ready for the UI. */
    data class TableData(
        val leagueId: String,
        val leagueName: String,
        val kind: Kind,
        val columns: List<Column>,
        val groups: List<Group>,
        val showForm: Boolean,
    ) {
        val conferences: List<String> = groups.mapNotNull { it.conference }.distinct()
        val hasDivisions: Boolean get() = conferences.isNotEmpty()

        /** Groups to render for [view] and active [conference]. */
        fun groupsFor(view: View, conference: String?): List<Group> {
            if (!hasDivisions) return groups
            val activeConf = conference?.takeIf { it in conferences } ?: conferences.first()
            return when (view) {
                View.DIVISION -> groups.filter { it.conference == activeConf }
                View.CONFERENCE -> listOf(
                    mergeGroup(
                        name = activeConf,
                        rows = groups.filter { it.conference == activeConf }.flatMap { it.rows },
                        bySeed = true,
                    ),
                )
                View.LEAGUE -> listOf(
                    mergeGroup(
                        name = leagueName,
                        rows = groups.flatMap { it.rows },
                        bySeed = false,
                    ),
                )
            }
        }

        private fun mergeGroup(name: String, rows: List<Standing>, bySeed: Boolean): Group {
            val sorted = when {
                bySeed && rows.isNotEmpty() && rows.all { it.seed != null } -> rows.sortedBy { it.seed }
                rows.any { it.sortValue != 0.0 } -> rows.sortedByDescending { it.sortValue }
                else -> rows
            }
            return Group(
                name = name,
                conference = null,
                rows = sorted.mapIndexed { index, row -> row.copy(rank = index + 1) },
            )
        }

        fun findTeam(teamId: String): Pair<Group, Standing>? {
            for (group in groups) {
                group.rows.firstOrNull { it.team.id == teamId }?.let { return group to it }
            }
            return null
        }

        /** "2nd in Premier League" or "1st in AFC East". */
        fun placeText(teamId: String): String? {
            val (group, row) = findTeam(teamId) ?: return null
            val pos = row.rank.takeIf { it > 0 } ?: (group.rows.indexOf(row) + 1)
            val where = if (groups.size == 1) leagueName else shortGroupName(group.name)
            return "${ordinal(pos)} in $where"
        }

        /** Playoff and play-in cut lines for merged US conference/league tables. */
        fun playoffLines(view: View): List<CutLine> {
            val conferenceView = (hasDivisions && view == View.CONFERENCE) || (!hasDivisions && groups.size > 1)
            val leagueView = !hasDivisions && groups.size == 1
            return when (leagueId) {
                "football/nfl" -> if (conferenceView) listOf(CutLine(7, "Playoff line")) else emptyList()
                "basketball/nba" -> if (conferenceView) listOf(CutLine(6, "Playoffs"), CutLine(10, "Play-In")) else emptyList()
                "hockey/nhl" -> if (conferenceView) listOf(CutLine(8, "Playoff line")) else emptyList()
                "baseball/mlb" -> if (conferenceView) listOf(CutLine(6, "Playoff line")) else emptyList()
                "soccer/usa.1" -> if (conferenceView || groups.size > 1) listOf(CutLine(9, "Playoff line")) else emptyList()
                "basketball/wnba" -> if (leagueView || view == View.LEAGUE) listOf(CutLine(8, "Playoff line")) else emptyList()
                else -> emptyList()
            }
        }

        /** Distinct qualification / relegation zones present in this table. */
        val zones: List<Zone>
            get() = groups.asSequence()
                .flatMap { it.rows.asSequence() }
                .mapNotNull { row ->
                    val label = row.note?.takeIf { it.isNotBlank() && !ORDINAL_IN_NOTE.containsMatchIn(it) }
                        ?: return@mapNotNull null
                    Zone(label, row.noteColor ?: zoneColor(label))
                }
                .distinctBy { it.label }
                .toList()

        /** Distinct clincher codes (`x`, `y`, `z`, `e`…) present in this table. */
        val clinchers: List<Pair<String, String>>
            get() = groups.asSequence()
                .flatMap { it.rows.asSequence() }
                .mapNotNull { row ->
                    val code = row.clincher?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    code to (row.clincherText?.takeIf { it.isNotBlank() } ?: clinchWords(code))
                }
                .distinctBy { it.first }
                .toList()
    }

    private val ORDINAL_IN_NOTE = Regex("""^\d+(?:st|nd|rd|th)\s+in\b""", RegexOption.IGNORE_CASE)

    /**
     * Generic columns in [PREFERRED] order, limited to those at least one row actually has a value
     * for. Preserves exact compatibility with callers that only have raw [Standing] rows.
     */
    fun columns(rows: List<Standing>): List<Column> {
        val present = rows.asSequence()
            .flatMap { it.values.asSequence() }
            .filter { it.value.isNotBlank() }
            .map { it.label }
            .toSet()
        val ordered = PREFERRED.filter { it in present } +
            present.filterNot { it in PREFERRED }.sorted()
        return ordered.take(MAX_COLUMNS).map { Column(it) }
    }

    /**
     * Sport-aware table builder: groups divisions/conferences, chooses the natural columns for the
     * sport, attaches recent form from [recentGames] when missing, and prepares legend metadata.
     */
    fun build(
        league: League?,
        rows: List<Standing>,
        recentGames: List<SportsEvent> = emptyList(),
    ): TableData {
        val leagueId = league?.id.orEmpty()
        val leagueName = league?.name ?: "League"
        val computedForm = if (leagueId.isNotEmpty() && recentGames.isNotEmpty()) {
            formFrom(recentGames, leagueId)
        } else {
            emptyMap()
        }
        val enriched = if (computedForm.isEmpty()) rows else rows.map { row ->
            if (row.form != null || row.athlete) row
            else row.copy(form = computedForm[row.team.id]?.takeIf { it.isNotEmpty() })
        }

        val kind = detectKind(league, enriched)
        val rawGroups = LinkedHashMap<Pair<String, String?>, MutableList<Standing>>()
        for (row in enriched) {
            val groupName = row.group?.takeIf { it.isNotBlank() } ?: leagueName
            rawGroups.getOrPut(groupName to row.conference) { ArrayList() } += row
        }
        val groups = rawGroups.map { (key, groupRows) ->
            val ranked = rankRows(groupRows)
            Group(name = key.first, conference = key.second, rows = ranked)
        }

        val showForm = kind != Kind.RACING && groups.any { g -> g.rows.any { !it.form.isNullOrBlank() } }
        val chosenColumns = chooseColumns(kind, enriched, showForm)

        return TableData(
            leagueId = leagueId,
            leagueName = leagueName,
            kind = kind,
            columns = chosenColumns,
            groups = groups,
            showForm = showForm,
        )
    }

    private fun detectKind(league: League?, rows: List<Standing>): Kind {
        if (rows.any { it.athlete } || league?.sport == Sport.RACING) return Kind.RACING
        val keys = rows.asSequence().flatMap { it.values.asSequence() }.map { it.key }.filter { it.isNotEmpty() }.toSet()
        val labels = rows.asSequence().flatMap { it.values.asSequence() }.map { it.label }.toSet()
        return when {
            "otLosses" in keys || "OTL" in labels || league?.sport == Sport.HOCKEY -> Kind.HOCKEY
            league?.sport in setOf(Sport.FOOTBALL, Sport.RUGBY, Sport.AUSSIE_RULES) -> Kind.TABLE
            "points" in keys && "ties" in keys && "winPercent" !in keys -> Kind.TABLE
            else -> Kind.RECORD
        }
    }

    private fun chooseColumns(kind: Kind, rows: List<Standing>, showForm: Boolean): List<Column> {
        fun hasStat(key: String, vararg fallbackLabels: String): Boolean =
            rows.any { row ->
                row.values.any { v ->
                    v.value.isNotBlank() && (v.key == key || v.label in fallbackLabels)
                }
            }

        val anyTies = rows.any { row ->
            row.values.any { v ->
                (v.key == "ties" || v.label in setOf("T", "D")) &&
                    v.value.isNotBlank() && v.value != "0"
            }
        }

        val spec: List<Pair<String, List<String>>> = when (kind) {
            Kind.TABLE -> listOf(
                "P" to listOf("gamesPlayed", "P", "GP"),
                "W" to listOf("wins", "W"),
                "D" to listOf("ties", "D", "T"),
                "L" to listOf("losses", "L"),
                "GD" to listOf("pointDifferential", "differential", "GD", "DIFF"),
                "PTS" to listOf("points", "PTS"),
            )
            Kind.HOCKEY -> listOf(
                "GP" to listOf("gamesPlayed", "GP", "P"),
                "W" to listOf("wins", "W"),
                "L" to listOf("losses", "L"),
                "OTL" to listOf("otLosses", "OTL"),
                "PTS" to listOf("points", "PTS"),
                "STRK" to listOf("streak", "STRK"),
            )
            Kind.RACING -> listOf(
                "PTS" to listOf("championshipPts", "points", "PTS"),
                "BEHIND" to listOf("behind", "BEHIND"),
            )
            Kind.RECORD -> buildList {
                add("W" to listOf("wins", "W"))
                add("L" to listOf("losses", "L"))
                if (anyTies) add("T" to listOf("ties", "T"))
                add("PCT" to listOf("winPercent", "PCT"))
                add("GB" to listOf("gamesBehind", "GB"))
                add("DIFF" to listOf("pointDifferential", "differential", "DIFF"))
                add("STRK" to listOf("streak", "STRK"))
            }
        }

        val matched = spec.mapNotNull { (heading, aliases) ->
            val primaryKey = aliases.first()
            val labelAliases = aliases.drop(1).toTypedArray()
            if (hasStat(primaryKey, heading, *labelAliases)) Column(heading, primaryKey) else null
        }.let { cols ->
            // When LAST 5 form dots are shown, drop STRK to save horizontal space.
            val trimmed = if (showForm) cols.filterNot { it.label == "STRK" } else cols
            trimmed.take(MAX_COLUMNS)
        }

        return if (matched.size >= 2 || (kind == Kind.RACING && matched.isNotEmpty())) {
            matched
        } else {
            columns(rows)
        }
    }

    private fun rankRows(rows: List<Standing>): List<Standing> {
        val ordered = when {
            rows.isNotEmpty() && rows.all { it.rank > 0 } -> rows.sortedBy { it.rank }
            rows.isNotEmpty() && rows.all { it.seed != null } -> rows.sortedBy { it.seed }
            else -> rows
        }
        return ordered.mapIndexed { index, row ->
            if (row.rank > 0) row else row.copy(rank = index + 1)
        }
    }

    /**
     * One row, already reduced to [columns] and padded so every row has the same width. Matches by
     * canonical stat [Column.key] first when present, then by [Column.label] and common aliases.
     */
    fun cells(row: Standing, columns: List<Column>): List<String> =
        columns.map { column -> cellValue(row, column) ?: MISSING }

    fun cellValue(row: Standing, column: Column): String? {
        val values = row.values
        val direct = (if (column.key.isNotEmpty()) values.firstOrNull { it.key == column.key } else null)
            ?: values.firstOrNull { it.label == column.label }
            ?: aliasesFor(column.label).firstNotNullOfOrNull { alias ->
                values.firstOrNull { it.key == alias || it.label == alias }
            }
        val raw = direct?.value?.takeIf { it.isNotBlank() } ?: return null
        return if (column.label == "GB" && raw == "-") "–" else raw
    }

    private fun aliasesFor(heading: String): List<String> = when (heading) {
        "P" -> listOf("gamesPlayed", "GP")
        "GP" -> listOf("gamesPlayed", "P")
        "W" -> listOf("wins")
        "D" -> listOf("ties", "T")
        "T" -> listOf("ties", "D")
        "L" -> listOf("losses")
        "OTL" -> listOf("otLosses")
        "GD" -> listOf("pointDifferential", "differential", "DIFF")
        "DIFF" -> listOf("pointDifferential", "differential", "GD")
        "PTS" -> listOf("points", "championshipPts")
        "PCT" -> listOf("winPercent")
        "GB" -> listOf("gamesBehind")
        "STRK" -> listOf("streak")
        "BEHIND" -> listOf("behind")
        else -> emptyList()
    }

    /** Last five results (oldest first, e.g. `"WDLWW"`) from finished games we have in memory/cache. */
    fun formFrom(events: List<SportsEvent>, leagueId: String): Map<String, String> {
        val byTeam = HashMap<String, MutableList<Pair<Long, Char>>>()
        for (event in events) {
            if (event.leagueId != leagueId || event.state != GameState.POST) continue
            val awayScore = event.away.score?.toDoubleOrNull() ?: continue
            val homeScore = event.home.score?.toDoubleOrNull() ?: continue
            fun outcome(us: Double, them: Double): Char = when {
                us > them -> 'W'
                us < them -> 'L'
                else -> 'D'
            }
            if (event.away.team.id.isNotBlank()) {
                byTeam.getOrPut(event.away.team.id) { ArrayList() } += event.startMs to outcome(awayScore, homeScore)
            }
            if (event.home.team.id.isNotBlank()) {
                byTeam.getOrPut(event.home.team.id) { ArrayList() } += event.startMs to outcome(homeScore, awayScore)
            }
        }
        return byTeam.mapValues { (_, list) ->
            list.sortedBy { it.first }.takeLast(5).map { it.second }.joinToString("")
        }
    }

    /** Human-readable one-line summary for a highlighted team in the table. */
    fun summaryLine(table: TableData, row: Standing): String {
        fun stat(label: String): String? = cellValue(row, Column(label))
        return when (table.kind) {
            Kind.TABLE -> listOfNotNull(
                stat("PTS")?.let { "$it pts" },
                listOfNotNull(
                    stat("W")?.let { "$it won" },
                    stat("D")?.let { "$it drawn" },
                    stat("L")?.let { "$it lost" },
                ).joinToString(", ").ifBlank { null },
                stat("GD")?.let { "GD $it" },
            ).joinToString(" · ")
            Kind.HOCKEY -> listOfNotNull(
                stat("PTS")?.let { "$it pts" },
                listOfNotNull(stat("W"), stat("L"), stat("OTL")).joinToString("–").ifBlank { null },
                streakWords(row.streak ?: stat("STRK")),
            ).joinToString(" · ")
            Kind.RACING -> listOfNotNull(
                stat("PTS")?.let { "$it pts" },
                stat("BEHIND")?.takeIf { it != "–" && it != MISSING }?.let { "${it.trimStart('-')} behind leader" },
            ).joinToString(" · ")
            Kind.RECORD -> listOfNotNull(
                listOfNotNull(
                    stat("W"),
                    stat("L"),
                    stat("T")?.takeIf { it != "0" },
                ).joinToString("–").ifBlank { row.record },
                streakWords(row.streak ?: stat("STRK")),
                stat("GB")?.takeIf { it !in setOf("–", "-", "0", "0.0", MISSING) }?.let { "$it GB" },
            ).joinToString(" · ")
        }
    }

    private fun streakWords(streak: String?): String? {
        val text = streak?.trim()?.takeIf { it.length >= 2 } ?: return null
        val count = text.drop(1).toIntOrNull() ?: return null
        if (count < 2) return null
        return when (text.first().uppercaseChar()) {
            'W' -> "$count wins in a row"
            'L' -> "$count losses in a row"
            else -> null
        }
    }

    fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$n$suffix"
    }

    /** "American Football Conference" -> "AFC"; division names like "AFC East" stay as they are. */
    fun shortGroupName(name: String): String = when (name) {
        "American Football Conference" -> "AFC"
        "National Football Conference" -> "NFC"
        "American League" -> "AL"
        "National League" -> "NL"
        "Eastern Conference" -> "East"
        "Western Conference" -> "West"
        else -> name
    }

    fun clinchWords(code: String): String = when (code.lowercase()) {
        "x" -> "Clinched playoff place"
        "y" -> "Clinched division"
        "z" -> "Clinched conference #1"
        "*" -> "Clinched best league record"
        "e" -> "Eliminated from playoffs"
        "p" -> "Clinched Presidents' Trophy"
        else -> "Clinched"
    }

    /** Fallback qualification / relegation colour when the feed provides a description without a hex colour. */
    fun zoneColor(label: String): Long {
        val lower = label.lowercase(Locale.ROOT)
        return when {
            "relegat" in lower || "eliminat" in lower -> 0xFFE53935
            "champions" in lower -> 0xFF2B7DE9
            "europa" in lower && "conference" !in lower -> 0xFFF39C12
            "conference" in lower -> 0xFF27AE60
            "play" in lower -> 0xFF8E6CEF
            "promot" in lower -> 0xFF2E9E5B
            else -> 0xFF5FA8E6
        }
    }

    internal fun formatDelta(delta: Double): String =
        if (delta == floor(delta)) delta.toLong().toString() else String.format(Locale.US, "%.1f", delta)
}
