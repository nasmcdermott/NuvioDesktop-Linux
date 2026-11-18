package com.nuvio.app.features.sports

/**
 * Turning a provider's standings into a table someone can read.
 *
 * The feed sends a different column set per sport (wins/losses/ties for football, points and
 * overtime losses for hockey, points and deductions for soccer), and each stat arrives as its own
 * little object. Drawing all of them gives thirty columns and no table, so this picks the handful
 * that actually answer "who is doing well" and drops the rest.
 *
 * Pure and provider-shaped on purpose: [StandingValue.label] is the provider's abbreviation ("W",
 * "PCT"), which is what a column heading wants, and nothing here reaches for the network.
 */
internal object SportsTable {

    /**
     * The order to try columns in, best first. Anything present in the provider's list and in this
     * roster is shown, up to [MAX_COLUMNS]; the rest is dropped rather than wrapped.
     */
    // Record first, then rate, then volume: the question a table answers fastest is "how many
    // did they win", not "how many points did they score".
    private val PREFERRED = listOf(
        "W", "L", "T", "OTL", "PCT",
        "GP", "P", "PTS", "DIFF", "GD",
        "PF", "PA", "GF", "GA", "GB", "STRK",
    )

    /** Wide enough for the usual four or five, narrow enough to never need horizontal scrolling. */
    const val MAX_COLUMNS = 6

    /** One column of the table: the provider's heading and where to find its value in a row. */
    data class Column(val label: String)

    /**
     * The columns to draw, in [PREFERRED] order, limited to those at least one row actually has a
     * value for. A column that is empty on every row would just be a stripe of dashes.
     */
    fun columns(rows: List<Standing>): List<Column> {
        val present = rows.asSequence()
            .flatMap { it.values.asSequence() }
            .map { it.label }
            .toSet()
        // Preferred first, then anything else the provider sent, so a league whose headings this
        // roster has never seen still shows numbers rather than an empty table.
        val ordered = PREFERRED.filter { it in present } +
            present.filterNot { it in PREFERRED }.sorted()
        return ordered.take(MAX_COLUMNS).map { Column(it) }
    }

    /**
     * One row, already reduced to [columns] and padded so every row has the same width. A missing
     * value is [MISSING], never an empty cell, so the columns stay aligned.
     */
    fun cells(row: Standing, columns: List<Column>): List<String> {
        val byLabel = row.values.associate { it.label to it.value }
        return columns.map { byLabel[it.label]?.takeIf { value -> value.isNotBlank() } ?: MISSING }
    }

    /** What stands in for a value the provider did not send. */
    const val MISSING = "—"
}
