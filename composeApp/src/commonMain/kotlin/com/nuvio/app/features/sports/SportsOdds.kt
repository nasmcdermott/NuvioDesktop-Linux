package com.nuvio.app.features.sports

/**
 * Away-side share for a two-color odds background. American odds are converted to implied
 * probability; when both sides are quoted, the probabilities are normalized to remove the
 * bookmaker's overround. If only one side is quoted, the other is treated as its complement.
 */
internal fun moneylineAwayShare(odds: MoneylineOdds?): Float {
    val awayProbability = odds?.away?.let(::americanOddsProbability)
    val homeProbability = odds?.home?.let(::americanOddsProbability)
    return when {
        awayProbability != null && homeProbability != null -> {
            val total = awayProbability + homeProbability
            if (total > 0.0) (awayProbability / total).toFloat() else 0.5f
        }
        awayProbability != null -> awayProbability.toFloat()
        homeProbability != null -> (1.0 - homeProbability).toFloat()
        else -> 0.5f
    }.coerceIn(0f, 1f)
}

/** Converts American odds (for example, +120 or -140) to an implied probability. */
private fun americanOddsProbability(value: String): Double? {
    val odds = value.trim()
        .replace('\u2212', '-')
        .replace(",", "")
        .toDoubleOrNull()
        ?.takeIf { it.isFinite() && it != 0.0 }
        ?: return null
    val magnitude = kotlin.math.abs(odds)
    return if (odds < 0.0) magnitude / (magnitude + 100.0) else 100.0 / (magnitude + 100.0)
}
