package com.nuvio.app.features.home.components

import androidx.compose.ui.graphics.Color
import com.nuvio.app.core.ui.NuvioScrimRamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Compose stores colour alpha at 8 bits, so alpha reads are good to 1/255. */
private const val ALPHA_TOLERANCE = 0.005f

class HomeTvStackedFadeTest {
    private val top = HOME_TV_STACKED_HERO_FADE_TOP_FRACTION
    private val solid = IMMERSIVE_STACKED_BOTTOM_FADE_SOLID_FRACTION

    /** Fade box fraction for a window fraction: the box spans [top] to the bottom edge. */
    private fun boxFraction(windowY: Float): Float = (windowY - top) / (1f - top)

    /** Alpha at a window fraction, linearly interpolated between the box stops. */
    private fun alphaAtWindow(stops: Array<Pair<Float, Color>>, windowY: Float): Float {
        val p = boxFraction(windowY)
        for (i in 0 until stops.size - 1) {
            val lo: Pair<Float, Color> = stops[i]
            val hi: Pair<Float, Color> = stops[i + 1]
            if (p in lo.first..hi.first) {
                val span = hi.first - lo.first
                val t = if (span == 0f) 0f else (p - lo.first) / span
                return lo.second.alpha + (hi.second.alpha - lo.second.alpha) * t
            }
        }
        return if (p < stops.first().first) stops.first().second.alpha else stops.last().second.alpha
    }

    @Test
    fun fadeIsBlackAndSpansFromSixtySixPointSevenPercentToTheBottomEdge() {
        val stops = immersiveHeroStackedBottomFadeStops()
        assertEquals(0f, stops.first().first, 0.0001f)
        assertEquals(1f, stops.last().first, 0.0001f)
        assertTrue(stops.all { it.second == Color.Black.copy(alpha = it.second.alpha) }, "fade must be black")
    }

    @Test
    fun alphaIsZeroAtTheFadeTop() {
        assertEquals(0f, alphaAtWindow(immersiveHeroStackedBottomFadeStops(), top), ALPHA_TOLERANCE)
    }

    @Test
    fun alphaIsHalfAtTheMidpointOfTheRamp() {
        // The linear ramp runs from the fade top to the solid fraction; its midpoint reads 0.5.
        val midpoint = (top + IMMERSIVE_STACKED_BOTTOM_FADE_SOLID_FRACTION) / 2f
        assertEquals(0.5f, alphaAtWindow(immersiveHeroStackedBottomFadeStops(), midpoint), ALPHA_TOLERANCE)
    }

    @Test
    fun alphaIsOneAtTheRampEndAndAtTheBottomEdge() {
        val stops = immersiveHeroStackedBottomFadeStops()
        assertEquals(1f, alphaAtWindow(stops, solid), ALPHA_TOLERANCE)
        assertEquals(1f, alphaAtWindow(stops, 1f), ALPHA_TOLERANCE)
    }

    @Test
    fun rampEndsAtTheSolidFractionOfTheWindow() {
        // The stop that reaches full opacity sits at the solid fraction of the window.
        val stops = immersiveHeroStackedBottomFadeStops()
        val solidStop = stops.first { it.second.alpha >= 1f }
        assertEquals(boxFraction(solid), solidStop.first, 0.0001f)
    }

    @Test
    fun stopCountIsWithinTheDitheredGradientsCap() {
        // The desktop gradient shader takes 1..MAX_STOPS stops and refuses anything longer at
        // construction, so the ramp has to be sampled within the cap rather than at its own density.
        val stops = immersiveHeroStackedBottomFadeStops()
        assertTrue(
            stops.size in 1..NuvioScrimRamp.MAX_STOPS,
            "the dithered gradient takes 1..${NuvioScrimRamp.MAX_STOPS} stops, got ${stops.size}",
        )
        assertEquals(NuvioScrimRamp.MAX_STOPS, stops.size)
    }

    @Test
    fun rampIsSampledAtTheQuartersOfTheRampSpanThenHoldsToTheBottom() {
        val stops = immersiveHeroStackedBottomFadeStops()
        val rampTop = HOME_TV_STACKED_HERO_FADE_TOP_FRACTION
        val rampEnd = IMMERSIVE_STACKED_BOTTOM_FADE_SOLID_FRACTION
        val expected = List(5) { i ->
            val t = i / 4f
            (rampTop + (rampEnd - rampTop) * t) to t
        } + (1f to 1f)
        assertEquals(expected.size, stops.size, "one stop per ramp sample, plus the solid bottom edge")
        expected.forEachIndexed { index, (windowY, alpha) ->
            val stop = stops[index]
            assertEquals(
                (windowY - rampTop) / (1f - rampTop),
                stop.first,
                1e-4f,
                "stop $index sits at window fraction $windowY",
            )
            assertEquals(alpha, stop.second.alpha, ALPHA_TOLERANCE, "stop $index alpha")
        }
    }

    @Test
    fun fadeAlphaNeverDecreases() {
        val stops = immersiveHeroStackedBottomFadeStops()
        val samples = (0..1000).map { alphaAtWindow(stops, top + (1f - top) * it / 1000f) }
        assertTrue(samples.zipWithNext().all { (a, b) -> b >= a - 1e-6f })
        assertTrue(stops.map { it.second.alpha }.zipWithNext().all { (a, b) -> b >= a })
    }
}

class HomeTvStackedSideScrimTest {
    private val background = Color.Black

    @Test
    fun stackedRampRunsFromOneToZeroAcrossFortyFivePercent() {
        val stops = immersiveHeroSideScrimStops(background, stackedWidthFraction = 0.45f)
        assertEquals(1f, stops.first().second.alpha, ALPHA_TOLERANCE)
        assertEquals(0f, stops.last().second.alpha, ALPHA_TOLERANCE)
        // Window fraction 0.22 of the 45% width sits at 0.099 of the hero box.
        assertEquals(0.45f * 0.22f, stops[1].first, 0.0001f)
        assertEquals(0.86f, stops[1].second.alpha, ALPHA_TOLERANCE)
        assertEquals(0.45f * 0.46f, stops[2].first, 0.0001f)
        assertEquals(0.56f, stops[2].second.alpha, ALPHA_TOLERANCE)
        assertEquals(0.45f * 0.76f, stops[3].first, 0.0001f)
        assertEquals(0.16f, stops[3].second.alpha, ALPHA_TOLERANCE)
        assertEquals(0.45f, stops[4].first, 0.0001f)
        assertEquals(0f, stops[4].second.alpha, ALPHA_TOLERANCE)
    }

    @Test
    fun stackedRampIsBlackOnly() {
        assertTrue(immersiveHeroSideScrimStops(background, 0.45f).all { it.second.red == 0f && it.second.green == 0f && it.second.blue == 0f })
    }

    @Test
    fun defaultRampIsUnchanged() {
        val stops = immersiveHeroSideScrimStops(background)
        assertEquals(0.96f, stops.first().second.alpha, ALPHA_TOLERANCE)
        assertEquals(0.70f, stops[4].first, 0.0001f)
    }
}
