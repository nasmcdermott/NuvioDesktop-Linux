package com.nuvio.app.features.sports

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NavAction
import com.nuvio.app.core.ui.arrowFocusDirection
import com.nuvio.app.core.ui.navAction
import com.nuvio.app.core.ui.requestFocusLogged
import com.nuvio.app.core.ui.trackSidebarEntryFocusY
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.settings.focusZoomScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shared interaction, focus-zoom and keyboard/controller navigation primitives for the Sports page.
 *
 * Mirrors the patterns used across Home, YouTube and Settings:
 *  - Every interactive surface owns a single [MutableInteractionSource] passed to [clickable]
 *    (never stacking a duplicate `.focusable()` node on top of `.clickable()`, which created two
 *    nested focus targets and swallowed arrow traversal).
 *  - Focus and hover scale follow Settings > Appearance > Focus zoom ([ThemeSettingsRepository.desktopFocusZoomPercent]
 *    + [focusZoomScale]).
 *  - Horizontal control strips and multi-row card grids connect via explicit Up/Down focus bands so
 *    keyboard arrows and gamepad D-pads never get stranded when targets sit in different columns or
 *    off-screen lazy rows.
 */
@Stable
internal class SportsInteractionState(
    val interactionSource: MutableInteractionSource,
    val focused: Boolean,
    val hovered: Boolean,
    val scale: Float,
) {
    val highlighted: Boolean get() = focused || hovered
}

@Composable
internal fun rememberSportsInteraction(
    baseScale: Float = 1.045f,
    label: String = "sportsFocusZoom",
): SportsInteractionState {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val zoomPercent by ThemeSettingsRepository.desktopFocusZoomPercent.collectAsStateWithLifecycle()
    val targetScale = if (focused || hovered) focusZoomScale(baseScale, zoomPercent) else 1f
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 135, easing = FastOutSlowInEasing),
        label = label,
    )
    return SportsInteractionState(
        interactionSource = interaction,
        focused = focused,
        hovered = hovered,
        scale = animatedScale,
    )
}

/**
 * Single-focus-stop interactive modifier with Nuvio focus-zoom scaling, hover tracking, sidebar Y
 * entry tracking, and rebound-Select / Spacebar activation.
 */
internal fun Modifier.sportsInteractive(
    state: SportsInteractionState,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    elevateOnFocus: Boolean = true,
    onFocused: (() -> Unit)? = null,
    onExtraKey: ((KeyEvent) -> Boolean)? = null,
    onClick: () -> Unit,
): Modifier = this
    .then(if (elevateOnFocus && state.highlighted) Modifier.zIndex(1f) else Modifier)
    .graphicsLayer {
        scaleX = state.scale
        scaleY = state.scale
    }
    .trackSidebarEntryFocusY()
    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
    .then(
        if (onFocused != null) {
            Modifier.onFocusChanged { if (it.isFocused) onFocused() }
        } else {
            Modifier
        },
    )
    // Key handler MUST precede clickable so Enter / rebound Select is handled on KeyUp cleanly.
    .onKeyEvent { event ->
        if (!enabled) return@onKeyEvent false
        if (onExtraKey != null && onExtraKey(event)) return@onKeyEvent true
        val activates = event.navAction() == NavAction.Select || event.key == Key.Spacebar
        if (!activates) return@onKeyEvent false
        when (event.type) {
            KeyEventType.KeyDown -> true
            KeyEventType.KeyUp -> {
                onClick()
                true
            }
            else -> true
        }
    }
    .hoverable(state.interactionSource, enabled = enabled)
    .clickable(
        enabled = enabled,
        interactionSource = state.interactionSource,
        indication = null,
        role = Role.Button,
        onClick = onClick,
    )

/**
 * Wraps a horizontal row or `LazyRow` as a named focus band in the vertical page ladder:
 *  - Entering the band via [bandRequester] restores the last focused child (or [fallbackRequester]).
 *  - Pressing Up routes to [onUp] (returning `false` at the top band so `tabSidebarHandoff` reaches
 *    the app's floating top navigation bar).
 *  - Pressing Down routes to [onDown].
 */
@Composable
internal fun Modifier.sportsHorizontalBand(
    bandRequester: FocusRequester,
    fallbackRequester: FocusRequester? = null,
    onUp: () -> Boolean,
    onDown: () -> Boolean,
): Modifier {
    val latestUp by rememberUpdatedState(onUp)
    val latestDown by rememberUpdatedState(onDown)
    return this
        .focusRequester(bandRequester)
        .focusRestorer(fallbackRequester ?: FocusRequester.Default)
        .focusGroup()
        .onPreviewKeyEvent { event ->
            when (arrowFocusDirection(event)) {
                FocusDirection.Up -> latestUp()
                FocusDirection.Down -> latestDown()
                else -> false
            }
        }
}

/**
 * Pure 2D layout index for a multi-section card grid where section headers span all [columns] and
 * cards wrap into rows of up to [columns] items per section.
 */
internal class SportsSectionGridLayout<T>(
    val entries: List<Entry<T>>,
    /** For each card row `r`, the `entries` index of each column `c` in that row. */
    val rows: List<List<Int>>,
) {
    sealed interface Entry<out T> {
        val key: String

        data class Header(
            override val key: String,
            val title: String,
            val count: Int,
            val live: Boolean,
            val accentColor: Long? = null,
            /** Right-aligned wording ("Everything you follow, soonest first"), in place of the count. */
            val caption: String? = null,
        ) : Entry<Nothing>

        data class Card<out T>(
            override val key: String,
            val item: T,
            val entryIndex: Int,
            val row: Int,
            val col: Int,
        ) : Entry<T>
    }

    val isEmpty: Boolean get() = rows.isEmpty()

    companion object {
        data class SectionInput<T>(
            val key: String,
            val title: String,
            val items: List<T>,
            val live: Boolean = false,
            val accentColor: Long? = null,
            /** Right-aligned wording, replacing the item count when set. */
            val caption: String? = null,
            val itemKey: (T) -> String,
        )

        fun <T> build(sections: List<SectionInput<T>>, columns: Int): SportsSectionGridLayout<T> {
            val safeCols = columns.coerceAtLeast(1)
            val entries = ArrayList<Entry<T>>()
            val rows = ArrayList<List<Int>>()

            for (section in sections) {
                if (section.items.isEmpty()) continue
                entries += Entry.Header(
                    key = "hdr:${section.key}",
                    title = section.title,
                    count = section.items.size,
                    live = section.live,
                    accentColor = section.accentColor,
                    caption = section.caption,
                )
                for (chunk in section.items.chunked(safeCols)) {
                    val rowIdx = rows.size
                    val rowEntryIndices = ArrayList<Int>(chunk.size)
                    chunk.forEachIndexed { colIdx, item ->
                        val entryIndex = entries.size
                        entries += Entry.Card(
                            key = "card:${section.key}:${section.itemKey(item)}",
                            item = item,
                            entryIndex = entryIndex,
                            row = rowIdx,
                            col = colIdx,
                        )
                        rowEntryIndices += entryIndex
                    }
                    rows += rowEntryIndices
                }
            }
            return SportsSectionGridLayout(entries, rows)
        }
    }
}

/**
 * Keyboard & controller focus controller for a [LazyGridState] backed by [SportsSectionGridLayout].
 * Handles multi-row Up/Down (including scrolling off-screen rows into view), Left/Right within rows,
 * Left-at-column-0 release to the app sidebar, and Up-from-row-0 handoff to the filter strip above.
 */
internal class SportsGridFocusController(
    val gridState: LazyGridState,
    private val scope: CoroutineScope,
) {
    private val requesters = HashMap<Int, FocusRequester>()
    var focusedRow by mutableIntStateOf(0)
    var focusedCol by mutableIntStateOf(0)

    fun requester(entryIndex: Int): FocusRequester =
        requesters.getOrPut(entryIndex) { FocusRequester() }

    fun recordFocus(row: Int, col: Int) {
        focusedRow = row
        focusedCol = col
    }

    /** Focuses the last remembered card (clamped to [layout]), or `(0, 0)` when entering from above. */
    fun focusGrid(layout: SportsSectionGridLayout<*>, resetToTop: Boolean = false): Boolean {
        if (layout.rows.isEmpty()) return false
        val targetRow = if (resetToTop) 0 else focusedRow.coerceIn(0, layout.rows.lastIndex)
        val targetCol = if (resetToTop) 0 else focusedCol.coerceIn(0, layout.rows[targetRow].lastIndex)
        val targetIndex = layout.rows[targetRow][targetCol]
        if (runCatching { requester(targetIndex).requestFocusLogged() }.getOrDefault(false)) {
            return true
        }
        scope.launch {
            scrollAndFocus(targetIndex, scrollAnchor = if (targetRow == 0) 0 else targetIndex)
        }
        return true
    }

    fun handleKey(
        event: KeyEvent,
        layout: SportsSectionGridLayout<*>,
        onUpFromTopRow: () -> Boolean,
    ): Boolean {
        val dir = arrowFocusDirection(event) ?: return false
        if (layout.rows.isEmpty()) return false
        val r = focusedRow.coerceIn(0, layout.rows.lastIndex)
        val c = focusedCol.coerceIn(0, layout.rows[r].lastIndex)

        return when (dir) {
            FocusDirection.Left -> {
                if (c == 0) {
                    // Release at column 0 so App's tabSidebarHandoff can enter the sidebar when drawn.
                    false
                } else {
                    val target = layout.rows[r][c - 1]
                    runCatching { requester(target).requestFocusLogged() }.getOrDefault(false) || true
                }
            }
            FocusDirection.Right -> {
                if (c < layout.rows[r].lastIndex) {
                    val target = layout.rows[r][c + 1]
                    runCatching { requester(target).requestFocusLogged() }.getOrDefault(false)
                }
                true
            }
            FocusDirection.Up -> {
                if (r == 0) {
                    scope.launch { runCatching { gridState.scrollToItem(0) } }
                    onUpFromTopRow()
                } else {
                    val nextRow = r - 1
                    val nextCol = c.coerceAtMost(layout.rows[nextRow].lastIndex)
                    val target = layout.rows[nextRow][nextCol]
                    scope.launch {
                        scrollAndFocus(target, scrollAnchor = if (nextRow == 0) 0 else target)
                    }
                    true
                }
            }
            FocusDirection.Down -> {
                if (r < layout.rows.lastIndex) {
                    val nextRow = r + 1
                    val nextCol = c.coerceAtMost(layout.rows[nextRow].lastIndex)
                    val target = layout.rows[nextRow][nextCol]
                    scope.launch {
                        scrollAndFocus(target, scrollAnchor = target)
                    }
                }
                true
            }
            else -> false
        }
    }

    private suspend fun scrollAndFocus(targetEntryIndex: Int, scrollAnchor: Int) {
        val viewportEnd = gridState.layoutInfo.viewportEndOffset
        val existing = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetEntryIndex }
        val fullyVisible = existing != null &&
            existing.offset.y >= 0 &&
            existing.offset.y + existing.size.height <= viewportEnd
        if (fullyVisible && runCatching { requester(targetEntryIndex).requestFocusLogged() }.getOrDefault(false)) {
            if (scrollAnchor == 0 && gridState.firstVisibleItemIndex > 0) {
                runCatching { gridState.scrollToItem(0) }
            }
            return
        }
        runCatching { gridState.scrollToItem(scrollAnchor) }
        repeat(8) {
            delay(24)
            if (runCatching { requester(targetEntryIndex).requestFocusLogged() }.getOrDefault(false)) return
        }
    }
}

@Composable
internal fun rememberSportsGridFocus(gridState: LazyGridState): SportsGridFocusController {
    val scope = rememberCoroutineScope()
    return remember(gridState) { SportsGridFocusController(gridState, scope) }
}

/**
 * Vertical list focus helper for `LazyColumn` tables and channel lists: ensures Up/Down scrolls
 * off-screen rows into view before focusing them, and routes Up from row 0 to [onUpFromFirst].
 */
internal class SportsListFocusController(
    val listState: LazyListState,
    private val scope: CoroutineScope,
) {
    private val requesters = HashMap<Int, FocusRequester>()
    var focusedRowIndex by mutableIntStateOf(0)

    fun requester(rowIndex: Int): FocusRequester =
        requesters.getOrPut(rowIndex) { FocusRequester() }

    fun focusRow(rowIndex: Int, lazyItemIndex: Int, totalRows: Int): Boolean {
        if (totalRows <= 0) return false
        val safeRow = rowIndex.coerceIn(0, totalRows - 1)
        if (runCatching { requester(safeRow).requestFocusLogged() }.getOrDefault(false)) return true
        scope.launch {
            scrollAndFocus(safeRow, lazyItemIndex)
        }
        return true
    }

    fun handleVerticalAndSideKeys(
        event: KeyEvent,
        rowCount: Int,
        lazyIndexOfRow: (Int) -> Int,
        onUpFromFirst: () -> Boolean,
        onRight: (() -> Boolean)? = null,
    ): Boolean {
        val dir = arrowFocusDirection(event) ?: return false
        if (rowCount <= 0) return false
        val current = focusedRowIndex.coerceIn(0, rowCount - 1)
        return when (dir) {
            FocusDirection.Up -> {
                if (current == 0) {
                    scope.launch { runCatching { listState.scrollToItem(0) } }
                    onUpFromFirst()
                } else {
                    val prev = current - 1
                    val lazyIdx = if (prev == 0) 0 else lazyIndexOfRow(prev)
                    scope.launch { scrollAndFocus(prev, lazyIdx) }
                    true
                }
            }
            FocusDirection.Down -> {
                if (current < rowCount - 1) {
                    val next = current + 1
                    scope.launch { scrollAndFocus(next, lazyIndexOfRow(next)) }
                }
                true
            }
            FocusDirection.Right -> onRight?.invoke() ?: false
            // Left releases so App's tabSidebarHandoff can enter the sidebar.
            FocusDirection.Left -> false
            else -> false
        }
    }

    private suspend fun scrollAndFocus(targetRow: Int, lazyItemIndex: Int) {
        val viewportEnd = listState.layoutInfo.viewportEndOffset
        val existing = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == lazyItemIndex }
        val fullyVisible = existing != null &&
            existing.offset >= 0 &&
            existing.offset + existing.size <= viewportEnd
        if (!fullyVisible) {
            val scrollTarget = (lazyItemIndex - 2).coerceAtLeast(0)
            runCatching { listState.scrollToItem(scrollTarget) }
        }
        repeat(8) {
            if (runCatching { requester(targetRow).requestFocusLogged() }.getOrDefault(false)) return
            delay(24)
        }
    }
}

@Composable
internal fun rememberSportsListFocus(listState: LazyListState): SportsListFocusController {
    val scope = rememberCoroutineScope()
    return remember(listState) { SportsListFocusController(listState, scope) }
}
