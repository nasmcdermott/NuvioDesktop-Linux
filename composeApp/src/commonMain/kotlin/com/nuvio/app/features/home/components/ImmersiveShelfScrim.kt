package com.nuvio.app.features.home.components

import androidx.compose.ui.graphics.Color
import com.nuvio.app.core.ui.NuvioScrimRamp

/**
 * The left-hand darkening that keeps the hero's logo, synopsis, ratings and cast panel legible when
 * the backdrop runs the full width of the window.
 *
 * The shelf-bounded layout does not need this: its backdrop only occupies the right-hand
 * `HERO_BACKDROP_WIDTH_FRACTION` and fades out over a short ramp just past its own left edge, so the
 * metadata column sits on background rather than on artwork. Full backdrop paints artwork under the
 * text as well, and reusing that short ramp left the column on an unscrimmed picture — with a bright
 * backdrop, the title and synopsis simply could not be read.
 *
 * So this is the game library's Full backdrop ramp, which exists for exactly this problem: a long
 * fall across the left 70% of the window rather than a quick one over the left 30%, holding a heavy
 * scrim through the whole text column and reaching zero well clear of it. The right-hand 30% is
 * never touched, so the artwork is still shown as supplied where nothing is written over it.
 */
internal fun immersiveHeroSideScrimStops(
    backgroundColor: Color,
    /**
     * Linux TV Mode stacked rows only: the fade's width as a fraction of the hero box. When set,
     * the ramp is the shorter stacked ramp, scaled into that width. Null keeps the Game Mode and
     * TV ramp exactly as before.
     */
    stackedWidthFraction: Float? = null,
): Array<Pair<Float, Color>> {
    if (stackedWidthFraction != null) {
        val w = stackedWidthFraction
        return arrayOf(
            0f to backgroundColor.copy(alpha = 1f),
            w * 0.22f to backgroundColor.copy(alpha = 0.86f),
            w * 0.46f to backgroundColor.copy(alpha = 0.56f),
            w * 0.76f to backgroundColor.copy(alpha = 0.16f),
            w to backgroundColor.copy(alpha = 0f),
            1f to backgroundColor.copy(alpha = 0f),
        )
    }
    return arrayOf(
        0f to backgroundColor.copy(alpha = 0.96f),
        0.22f to backgroundColor.copy(alpha = 0.74f),
        0.42f to backgroundColor.copy(alpha = 0.44f),
        0.60f to backgroundColor.copy(alpha = 0.125f),
        0.70f to backgroundColor.copy(alpha = 0f),
        1f to backgroundColor.copy(alpha = 0f),
    )
}

/** Linux TV Mode stacked left fade: 45% of the hero box width. */
internal const val IMMERSIVE_STACKED_SIDE_SCRIM_WIDTH_FRACTION = 0.45f

/**
 * The darkening laid over the bottom of a TV-style screen so the shelf's titles and posters stay
 * legible against whatever artwork is behind them.
 *
 * Two separate layers land in this region and multiply together: the hero's own bottom fade
 * ([immersiveHeroBottomFadeStops]) and the shelf container's background
 * ([immersiveShelfScrimStops]). Tuning either in isolation is how the region ends up either
 * unreadable or solid black, so both live here and `ImmersiveShelfScrimTest` renders them stacked
 * and reads the result back.
 *
 * With [fullBackdrop] off, the stack reaches the opaque background colour — that is TV Mode's black
 * shelf, and the hero deliberately stops painting artwork above it. With it on, the artwork runs to
 * the bottom of the screen and both layers stop short, so the shelf reads as floating over the
 * picture. This mirrors the game library's Black shelf / Full backdrop choice.
 */
internal fun immersiveShelfScrimStops(
    backgroundColor: Color,
    fullBackdrop: Boolean,
    // Lighter when the ambient background is on (TV ambient, Collections' folder view): the wash
    // already darkens toward the bottom, and a heavy scrim here cut its colour off above the rows.
    // Irrelevant under full backdrop, which never reaches opaque.
    ambientBackgroundEnabled: Boolean = false,
): Array<Pair<Float, Color>> = if (fullBackdrop) {
    arrayOf(
        0f to backgroundColor.copy(alpha = 0f),
        0.30f to backgroundColor.copy(alpha = 0.08f),
        0.62f to backgroundColor.copy(alpha = 0.38f),
        1f to backgroundColor.copy(alpha = 0.68f),
    )
} else {
    arrayOf(
        0f to backgroundColor.copy(alpha = 0f),
        0.30f to backgroundColor.copy(alpha = if (ambientBackgroundEnabled) 0.14f else 0.28f),
        // Ambient holds the row-top level to the bottom rather than darkening further.
        0.62f to backgroundColor.copy(alpha = if (ambientBackgroundEnabled) 0.14f else 0.88f),
        1f to backgroundColor.copy(alpha = if (ambientBackgroundEnabled) 0.14f else 1f),
    )
}

/**
 * The hero's own bottom fade, which sits under [immersiveShelfScrimStops] in the shelf region.
 *
 * Under [fullBackdrop] this is deliberately much lighter than the shelf-bounded version: it is no
 * longer the thing ending the artwork, only a gradient easing into the shelf's own scrim, and at
 * the old strength the two multiplied out to very nearly opaque.
 */
internal fun immersiveHeroBottomFadeStops(
    backgroundColor: Color,
    fullBackdrop: Boolean,
): Array<Pair<Float, Color>> = if (fullBackdrop) {
    arrayOf(
        0f to backgroundColor.copy(alpha = 0f),
        0.30f to backgroundColor.copy(alpha = 0.06f),
        0.60f to backgroundColor.copy(alpha = 0.18f),
        1f to backgroundColor.copy(alpha = 0.42f),
    )
} else {
    // Spread the ramp so the artwork dissolves gradually instead of a quick band to solid.
    arrayOf(
        0f to backgroundColor.copy(alpha = 0f),
        0.30f to backgroundColor.copy(alpha = 0.10f),
        0.55f to backgroundColor.copy(alpha = 0.35f),
        0.80f to backgroundColor.copy(alpha = 0.72f),
        1f to backgroundColor,
    )
}

/** Window fraction where the stacked bottom fade reaches solid black when it is not measured from the rows. */
internal const val IMMERSIVE_STACKED_BOTTOM_FADE_SOLID_FRACTION = 0.58f

/**
 * Bottom fade for Linux TV Mode's stacked rows. The artwork is fully unobscured above [top]
 * (the nav bar). Black opacity rises linearly from 0 at [top] to 1 at [solid] (the artwork's
 * bottom, measured from the layout), then stays solid black to the bottom edge. The fade box spans
 * from [top] to the bottom (1·H). The fade is black with alpha only, and alpha never decreases.
 *
 * The linear ramp is sampled within the dithered gradient's stop cap: the ramp samples plus the
 * solid endpoint meet it, leaving the segment from the ramp's end to the bottom edge as one
 * unbroken solid span.
 */
internal fun immersiveHeroStackedBottomFadeStops(
    top: Float = HOME_TV_STACKED_HERO_FADE_TOP_FRACTION,
    solid: Float = IMMERSIVE_STACKED_BOTTOM_FADE_SOLID_FRACTION,
): Array<Pair<Float, Color>> {
    val end = solid.coerceIn(top + 0.01f, 1f)
    val sampleCount = NuvioScrimRamp.MAX_STOPS - 2
    val windowStops = ArrayList<Pair<Float, Float>>(sampleCount + 1)
    for (i in 0..sampleCount) {
        val t = i.toFloat() / sampleCount
        // Linear: alpha equals the ramp position, so the 5 ramp stops fall at 0, 0.25, 0.5, 0.75, 1.
        val eased = t
        windowStops += (top + (end - top) * t) to eased
    }
    windowStops += 1f to 1f
    return windowStops
        .map { (y, alpha) -> ((y - top) / (1f - top)) to Color.Black.copy(alpha = alpha) }
        .toTypedArray()
}
