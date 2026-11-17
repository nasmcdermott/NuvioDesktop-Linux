package com.nuvio.app.features.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvio
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** One pill in the date strip. */
internal data class SportsDay(
    /** `yyyyMMdd`, the key the repository and the feed both use. */
    val day: String,
    /** "Today", "Yesterday", "Tomorrow", else the month and day ("Nov 20"). */
    val label: String,
    /** The weekday, always: "Thu". */
    val weekday: String,
)

private val DayKeyFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

/**
 * The days the strip offers, [radius] either side of [today].
 *
 * Built from the user's own zone and locale rather than a fixed English list: the rest of the app
 * already follows the system locale, and a dated strip that does not is the one place that reads
 * as foreign.
 */
internal fun sportsDays(
    today: LocalDate = LocalDate.now(),
    radius: Int = 3,
    locale: Locale = Locale.getDefault(),
): List<SportsDay> = (-radius..radius).map { offset ->
    val date = today.plusDays(offset.toLong())
    val label = when (offset) {
        -1 -> "Yesterday"
        0 -> "Today"
        1 -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("MMM d", locale))
    }
    SportsDay(
        day = date.format(DayKeyFormatter),
        label = label,
        weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
    )
}

/**
 * The date strip. Each pill is its own focus stop, so a gamepad's left/right lands on a day the
 * same way it lands on a channel in Live TV, and the mouse gets the same result.
 */
@Composable
internal fun SportsDateStrip(
    days: List<SportsDay>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        days.forEach { day ->
            SportsDayPill(
                day = day,
                selected = day.day == selected,
                onClick = { onSelect(day.day) },
            )
        }
    }
}

@Composable
private fun SportsDayPill(day: SportsDay, selected: Boolean, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    val labelColor = if (selected) tokens.colors.onAccent else tokens.colors.textPrimary
    val sublabelColor = if (selected) tokens.colors.onAccent.copy(alpha = tokens.opacity.secondary) else tokens.colors.textSecondary
    Column(
        modifier = Modifier
            .widthIn(min = 80.dp)
            .clip(shape)
            .background(
                when {
                    selected -> tokens.colors.accentFill
                    focused -> SolidColor(tokens.colors.focusBackground)
                    else -> SolidColor(tokens.colors.surfaceCard)
                },
            )
            .border(
                tokens.borders.hairline,
                when {
                    selected -> tokens.colors.borderSelected
                    focused -> tokens.colors.borderFocus
                    else -> tokens.colors.borderSubtle
                },
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(
                horizontal = tokens.components.chipHorizontalPadding,
                vertical = tokens.components.chipVerticalPadding,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = day.weekday,
            style = MaterialTheme.typography.labelSmall,
            color = sublabelColor,
        )
        Text(
            text = day.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = labelColor,
        )
    }
}
