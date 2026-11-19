package com.nuvio.app.features.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.material.icons.rounded.SportsFootball
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.SportsHockey
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsBaseball
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.SportsRugby
import androidx.compose.material.icons.rounded.SportsMma
import androidx.compose.material.icons.rounded.SportsMotorsports
import androidx.compose.material.icons.rounded.SportsGolf
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.material.icons.rounded.SportsCricket
import androidx.compose.material.icons.rounded.SportsKabaddi
import androidx.compose.material.icons.rounded.SportsVolleyball
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.smoothBorder

/**
 * The sport filter, one chip per sport a league exists for.
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

/** The glyph the reference page gives each sport's chip; null where the icon set has none. */
internal fun sportIcon(sport: Sport): ImageVector? = when (sport) {
    Sport.AMERICAN_FOOTBALL -> Icons.Rounded.SportsFootball
    Sport.BASKETBALL -> Icons.Rounded.SportsBasketball
    Sport.HOCKEY -> Icons.Rounded.SportsHockey
    Sport.BASEBALL -> Icons.Rounded.SportsBaseball
    Sport.FOOTBALL -> Icons.Rounded.SportsSoccer
    Sport.RUGBY -> Icons.Rounded.SportsRugby
    Sport.FIGHTING -> Icons.Rounded.SportsMma
    Sport.RACING -> Icons.Rounded.SportsMotorsports
    Sport.GOLF -> Icons.Rounded.SportsGolf
    Sport.TENNIS -> Icons.Rounded.SportsTennis
    Sport.CRICKET -> Icons.Rounded.SportsCricket
    Sport.WRESTLING -> Icons.Rounded.SportsKabaddi
    Sport.VOLLEYBALL -> Icons.Rounded.SportsVolleyball
    Sport.NETBALL -> Icons.Rounded.SportsVolleyball
    Sport.DARTS, Sport.SNOOKER -> Icons.Rounded.SportsScore
    else -> null
}

@Composable
internal fun SportsChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onExtraKey: ((KeyEvent) -> Boolean)? = null,
) {
    val tokens = MaterialTheme.nuvio
    val interaction = rememberSportsInteraction(baseScale = 1.05f, label = "sportsChipZoom")
    val shape = tokens.shapes.chip
    val borderWidth = if (interaction.focused) 2.dp else tokens.borders.hairline

    Box(
        modifier = modifier
            .sportsInteractive(
                state = interaction,
                focusRequester = focusRequester,
                onExtraKey = onExtraKey,
                onClick = onClick,
            )
            .clip(shape)
            .background(
                when {
                    selected -> tokens.colors.accentFill
                    interaction.highlighted -> SolidColor(tokens.colors.focusBackground)
                    else -> SolidColor(tokens.colors.surfaceCard)
                },
            )
            .smoothBorder(
                width = borderWidth,
                color = when {
                    interaction.focused -> tokens.colors.borderFocus
                    selected -> tokens.colors.borderSelected
                    else -> tokens.colors.borderSubtle
                },
                shape = shape,
            )
            .padding(
                horizontal = tokens.components.chipHorizontalPadding,
                vertical = tokens.components.chipVerticalPadding,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            icon?.let { iconVector ->
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = if (selected) tokens.colors.onAccent else tokens.colors.textSecondary,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected || interaction.focused) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) tokens.colors.onAccent else tokens.colors.textPrimary,
                maxLines = 1,
            )
        }
    }
}
