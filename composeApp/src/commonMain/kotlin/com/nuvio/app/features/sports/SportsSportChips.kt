package com.nuvio.app.features.sports

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.nuvio.app.core.ui.NavAction
import com.nuvio.app.core.ui.navAction
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import com.nuvio.app.core.ui.nuvio

/**
 * The sport filter, one chip per sport a league exists for.
 *
 * Null means "all sports", which is why the row always leads with it: a filter the user cannot get
 * back out of is a trap, and on a gamepad the way out must be visible.
 */
@Composable
internal fun SportsSportChips(
    sports: List<Sport>,
    selected: Sport?,
    onSelect: (Sport?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SportsChip(label = "All", selected = selected == null, onClick = { onSelect(null) })
        sports.forEach { sport ->
            SportsChip(
                label = sport.label,
                selected = sport == selected,
                onClick = { onSelect(sport) },
            )
        }
    }
}

@Composable
internal fun SportsChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    var focused by remember { mutableStateOf(false) }
    val shape = tokens.shapes.chip
    Box(
        modifier = Modifier
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
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) tokens.colors.onAccent else tokens.colors.textPrimary,
        )
    }
}
