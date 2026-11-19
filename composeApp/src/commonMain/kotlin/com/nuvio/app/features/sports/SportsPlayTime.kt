package com.nuvio.app.features.sports

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Format a play's wall-clock instant in the device timezone, including its daylight-aware zone code. */
internal fun formatSportsPlayTime(
    epochMs: Long,
    twelveHour: Boolean,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val pattern = if (twelveHour) "h:mm a z" else "HH:mm z"
    return DateTimeFormatter.ofPattern(pattern, Locale.US)
        .format(Instant.ofEpochMilli(epochMs).atZone(zone))
}
