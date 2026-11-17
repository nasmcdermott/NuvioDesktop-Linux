package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True inside the Collections screens. Landscape posters are a global preference, but the cards
 * are styled deep inside shared shelf components, so the surface marks itself here rather than
 * threading a flag through every call site — see [effectiveHomePosterCardStyle].
 */
internal val LocalCollectionsPosterSurface = staticCompositionLocalOf { false }

@Composable
internal fun rememberPosterCardStyleUiState(): PosterCardStyleUiState {
    PosterCardStyleRepository.ensureLoaded()
    val uiState by PosterCardStyleRepository.uiState.collectAsState()
    return uiState
}

/**
 * Poster styling shared by the Home, Search, Library, and Collections surfaces.
 * TV Mode no longer overrides the saved Hide labels preference.
 */
@Composable
internal fun rememberHomePosterCardStyleUiState(): PosterCardStyleUiState {
    val base = rememberPosterCardStyleUiState()
    return effectiveHomePosterCardStyle(
        base = base,
        isCollectionsSurface = LocalCollectionsPosterSurface.current,
    )
}

internal fun effectiveHomePosterCardStyle(
    base: PosterCardStyleUiState,
    isCollectionsSurface: Boolean = false,
): PosterCardStyleUiState {
    var style = base
    if (isCollectionsSurface && style.catalogLandscapeModeEnabled && style.collectionsPortraitPostersEnabled) {
        style = style.copy(catalogLandscapeModeEnabled = false)
    }
    return style
}
