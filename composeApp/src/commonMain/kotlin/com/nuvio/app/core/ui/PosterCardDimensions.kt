package com.nuvio.app.core.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal const val PosterLandscapeAspectRatio = 1.77f
internal const val PosterLandscapeWidthScale = 180f / 110f

/**
 * The portrait poster rectangle's width / height. Shared by every surface that draws a poster
 * card (Home rows, Library, Discover, the catalog grids), so the same saved poster width always
 * produces the same rectangle wherever it appears.
 */
internal const val PosterAspectRatio = 0.675f

internal fun landscapePosterWidth(basePosterWidthDp: Int): Dp =
    (basePosterWidthDp * PosterLandscapeWidthScale).dp

internal fun landscapePosterHeightForWidth(width: Dp): Dp =
    (width.value / PosterLandscapeAspectRatio).dp

/** Height of the portrait poster rectangle for a given saved/derived card width. */
internal fun posterHeightForWidth(width: Dp): Dp =
    (width.value / PosterAspectRatio).dp
