package com.nuvio.app.features.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioModalDialog
import com.nuvio.app.core.ui.NuvioTextField
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.smoothBorder
import com.nuvio.app.features.iptv.IptvChannelKind
import com.nuvio.app.features.iptv.IptvChannelMerge
import com.nuvio.app.features.settings.SettingsGlassButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Pure off-UI-thread search index and ranker for the manual Channel Picker dialog.
 */
internal object SportsChannelPickerSearch {

    data class IndexedChannel(
        val item: SportsChannels.Available,
        val normName: String,
        val normMeta: String,
    )

    fun buildIndex(channels: List<SportsChannels.Available>): List<IndexedChannel> =
        channels.map { item ->
            IndexedChannel(
                item = item,
                normName = SportsChannelMatcher.normalize(item.channel.name.replace("+", " plus ")),
                normMeta = SportsChannelMatcher.normalize("${item.categoryName} ${item.playlist.name}"),
            )
        }

    /**
     * Filters [index] by every token in [query] (matching channel name first, then category/playlist).
     * When [query] is blank, suggests sports-category channels and channels matching [seedTerms].
     */
    fun search(
        index: List<IndexedChannel>,
        query: String,
        seedTerms: List<String> = emptyList(),
        playlistFilter: String? = null,
        limit: Int = 80,
    ): List<SportsChannels.Available> {
        val scoped = if (playlistFilter.isNullOrBlank()) {
            index
        } else {
            index.filter { it.item.playlist.id == playlistFilter }
        }
        val tokens = SportsChannelMatcher.normalize(query.replace("+", " plus "))
            .split(' ')
            .filter { it.isNotEmpty() }

        if (tokens.isEmpty()) {
            val normSeeds = seedTerms.map { SportsChannelMatcher.normalize(it.replace("+", " plus ")) }
                .filter { it.length >= 2 }
            val seeded = if (normSeeds.isEmpty()) {
                emptyList()
            } else {
                scoped.filter { entry ->
                    normSeeds.any { seed -> SportsChannelMatcher.hasWord(entry.normName, seed) }
                }.take(limit)
            }
            if (seeded.size >= 12) return seeded.map { it.item }
            val seen = seeded.mapTo(HashSet()) { it.item.refKey }
            val sportChannels = scoped.asSequence()
                .filter { it.item.kind == IptvChannelKind.Sport && it.item.refKey !in seen }
                .take(limit - seeded.size)
                .toList()
            return (seeded + sportChannels).ifEmpty { scoped.take(limit) }.map { it.item }
        }

        val nameHits = ArrayList<SportsChannels.Available>()
        val metaHits = ArrayList<SportsChannels.Available>()
        for (entry in scoped) {
            val inName = tokens.all { tok -> entry.normName.contains(tok) }
            if (inName) {
                nameHits += entry.item
                if (nameHits.size >= limit) break
            } else if (nameHits.size + metaHits.size < limit) {
                val combined = "${entry.normName} ${entry.normMeta}"
                if (tokens.all { tok -> combined.contains(tok) }) {
                    metaHits += entry.item
                }
            }
        }
        return (nameHits + metaHits).take(limit)
    }
}

/**
 * Manual channel picker & multi-scope "Use this channel for more games?" dialog, inspired by
 * NuvioPlusIPTV's `ChannelPickerDialog` + `FixChannelDialog`.
 */
@Composable
internal fun SportsChannelPickerDialog(
    event: SportsEvent,
    league: League?,
    settings: SportsSettings,
    onDismiss: () -> Unit,
    onSavedAndWatch: ((SportsChannels.Available) -> Unit)? = null,
) {
    val tokens = MaterialTheme.nuvio
    var query by rememberSaveable { mutableStateOf("") }
    var selectedPlaylistId by rememberSaveable { mutableStateOf("") }
    var chosen by remember { mutableStateOf<SportsChannels.Available?>(null) }

    val allChannels = remember(settings.preferredPlaylistId) {
        SportsChannels.allForPicker(settings.preferredPlaylistId)
    }
    val playlists = remember(allChannels) {
        allChannels.map { it.playlist }.distinctBy { it.id }
    }
    val index by produceState<List<SportsChannelPickerSearch.IndexedChannel>?>(null, allChannels) {
        value = withContext(Dispatchers.Default) {
            SportsChannelPickerSearch.buildIndex(allChannels)
        }
    }
    val seedTerms = remember(event, league) {
        buildList {
            addAll(event.allNetworks)
            add(event.away.team.shortName)
            add(event.home.team.shortName)
            if (league != null) add(league.shortName)
        }.filter { it.isNotBlank() }
    }

    val results by produceState(emptyList<SportsChannels.Available>(), index, query, selectedPlaylistId, seedTerms) {
        val built = index ?: return@produceState
        value = withContext(Dispatchers.Default) {
            ensureActive()
            SportsChannelPickerSearch.search(
                index = built,
                query = query,
                seedTerms = seedTerms,
                playlistFilter = selectedPlaylistId.ifBlank { null },
            )
        }
    }

    val picked = chosen
    if (picked != null) {
        SportsPickScopeDialog(
            event = event,
            league = league,
            picked = picked,
            onBack = { chosen = null },
            onDismiss = onDismiss,
            onSavedAndWatch = onSavedAndWatch,
        )
        return
    }

    NuvioModalDialog(
        onDismissRequest = onDismiss,
        title = "Pick a channel for ${event.displayTitle}",
        subtitle = if (allChannels.isEmpty()) {
            "No IPTV channels are loaded yet. Add or refresh a playlist in Live TV settings."
        } else {
            "Search ${allChannels.size} channels across your IPTV playlists."
        },
        maxWidth = 700.dp,
        actions = {
            SettingsGlassButton(text = "Cancel", onClick = onDismiss)
        },
    ) {
        NuvioTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Search channel, network or category (e.g. ESPN, Sky Sports, NFL)…",
        )

        if (playlists.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item(key = "pl:all") {
                    SportsSmallPill(
                        label = "All playlists",
                        selected = selectedPlaylistId.isEmpty(),
                        onClick = { selectedPlaylistId = "" },
                    )
                }
                items(playlists, key = { "pl:${it.id}" }) { playlist ->
                    SportsSmallPill(
                        label = playlist.name.ifBlank { "Playlist" },
                        selected = selectedPlaylistId == playlist.id,
                        onClick = { selectedPlaylistId = playlist.id },
                    )
                }
            }
        }

        when {
            index == null -> Text(
                text = "Indexing channels…",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
            )
            results.isEmpty() -> Text(
                text = if (query.isBlank()) "No channels available." else "No channels match “$query”.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
            )
            else -> {
                val listState = rememberLazyListState()
                val listFocus = rememberSportsListFocus(listState)
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .onKeyEvent { event ->
                            listFocus.handleVerticalAndSideKeys(
                                event = event,
                                rowCount = results.size,
                                lazyIndexOfRow = { it },
                                onUpFromFirst = { false },
                            )
                        },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(results, key = { _, item -> item.refKey }) { index, item ->
                        PickerChannelRow(
                            item = item,
                            focusRequester = listFocus.requester(index),
                            onFocused = { listFocus.focusedRowIndex = index },
                            onClick = { chosen = item },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SportsPickScopeDialog(
    event: SportsEvent,
    league: League?,
    picked: SportsChannels.Available,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onSavedAndWatch: ((SportsChannels.Available) -> Unit)?,
) {
    val channelLabel = picked.channel.name.ifBlank { "Channel ${picked.channel.num}" }
    val refKey = picked.refKey
    val isFieldEvent = event.away.team.id.endsWith(":event")

    fun finish(watchAfter: Boolean, saveAction: () -> Unit) {
        // Unblock in case the user previously marked this channel wrong for this game.
        SportsSettingsRepository.unblockChannel(event.id, picked.channel.key)
        SportsSettingsRepository.unblockChannel(event.id, refKey)
        saveAction()
        onDismiss()
        if (watchAfter) onSavedAndWatch?.invoke(picked)
    }

    val scopeChoices = buildList {
        add(
            ScopeChoice(
                title = "Just this game (${event.displayTitle})",
                subtitle = "Overrides automatic matching for this fixture only",
                onClick = {
                    finish(watchAfter = false) {
                        SportsSettingsRepository.setPick(event.id, refKey)
                    }
                },
            ),
        )
        event.allNetworks.distinctBy { SportsChannelMatcher.networkKey(it) }.take(4).forEach { net ->
            add(
                ScopeChoice(
                    title = "Every $net broadcast",
                    subtitle = "Uses $channelLabel whenever a game lists $net",
                    onClick = {
                        finish(watchAfter = false) {
                            SportsSettingsRepository.setPick(event.id, refKey)
                            SportsSettingsRepository.setNetworkChannel(net, refKey)
                        }
                    },
                ),
            )
        }
        if (!isFieldEvent) {
            listOf(event.away.team, event.home.team)
                .filter { it.id.isNotBlank() }
                .distinctBy { it.id }
                .forEach { team ->
                    add(
                        ScopeChoice(
                            title = "Every ${team.name} game",
                            subtitle = "Great for a team's regional sports network or club channel",
                            onClick = {
                                finish(watchAfter = false) {
                                    SportsSettingsRepository.setPick(event.id, refKey)
                                    SportsSettingsRepository.setTeamChannel(
                                        SportsFollows.teamKey(event.leagueId, team.id),
                                        refKey,
                                    )
                                }
                            },
                        ),
                    )
                }
        }
        if (league != null) {
            add(
                ScopeChoice(
                    title = "Every ${league.name} event",
                    subtitle = "Uses $channelLabel across ${league.name}",
                    onClick = {
                        finish(watchAfter = false) {
                            SportsSettingsRepository.setPick(event.id, refKey)
                            SportsSettingsRepository.setLeagueChannel(league.id, refKey)
                        }
                    },
                ),
            )
        }
    }
    val listState = rememberLazyListState()
    val listFocus = rememberSportsListFocus(listState)

    NuvioModalDialog(
        onDismissRequest = onDismiss,
        title = "Use $channelLabel for…",
        subtitle = "Save this channel just for ${event.displayTitle}, or reuse it automatically whenever the same " +
            "team, network or competition plays.",
        maxWidth = 620.dp,
        actions = {
            SettingsGlassButton(text = "Back", onClick = onBack)
            SettingsGlassButton(
                text = "Just this game & Watch",
                accent = true,
                onClick = {
                    finish(watchAfter = true) {
                        SportsSettingsRepository.setPick(event.id, refKey)
                    }
                },
            )
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .onKeyEvent { event ->
                    listFocus.handleVerticalAndSideKeys(
                        event = event,
                        rowCount = scopeChoices.size,
                        lazyIndexOfRow = { it },
                        onUpFromFirst = { false },
                    )
                },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(scopeChoices, key = { _, choice -> choice.title }) { index, choice ->
                ScopeChoiceRow(
                    title = choice.title,
                    subtitle = choice.subtitle,
                    focusRequester = listFocus.requester(index),
                    onFocused = { listFocus.focusedRowIndex = index },
                    onClick = choice.onClick,
                )
            }
        }
    }
}

private data class ScopeChoice(
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)

/**
 * Region & language filter modal for the game page's "Watch on your channels" section.
 */
@Composable
internal fun SportsRegionFilterDialog(
    codes: Collection<String?>,
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val choices = remember(codes, current) { SportsChannelRegions.choicesFor(codes, current) }
    fun countLabel(count: Int): String = if (count == 1) "1 channel" else "$count channels"

    NuvioModalDialog(
        onDismissRequest = onDismiss,
        title = "Channels from",
        subtitle = "Prioritises and filters channels from your chosen region or language across every game. " +
            "Other regions stay one click away.",
        maxWidth = 520.dp,
        actions = {
            SettingsGlassButton(text = "Close", onClick = onDismiss)
        },
    ) {
        val listState = rememberLazyListState()
        val listFocus = rememberSportsListFocus(listState)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 340.dp)
                .onKeyEvent { event ->
                    listFocus.handleVerticalAndSideKeys(
                        event = event,
                        rowCount = choices.size + 1,
                        lazyIndexOfRow = { it },
                        onUpFromFirst = { false },
                    )
                },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "region:all") {
                ScopeChoiceRow(
                    title = (if (current.isBlank()) "✓ " else "") + "All regions",
                    subtitle = countLabel(codes.size),
                    selected = current.isBlank(),
                    focusRequester = listFocus.requester(0),
                    onFocused = { listFocus.focusedRowIndex = 0 },
                    onClick = { onPick(""); onDismiss() },
                )
            }
            itemsIndexed(choices, key = { _, item -> "region:${item.first}" }) { index, (code, count) ->
                val rowIndex = index + 1
                ScopeChoiceRow(
                    title = (if (code == current) "✓ " else "") + SportsChannelRegions.name(code),
                    subtitle = if (count == 0) "None for this game" else countLabel(count),
                    selected = code == current,
                    focusRequester = listFocus.requester(rowIndex),
                    onFocused = { listFocus.focusedRowIndex = rowIndex },
                    onClick = { onPick(code); onDismiss() },
                )
            }
        }
    }
}

@Composable
private fun PickerChannelRow(
    item: SportsChannels.Available,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val interaction = rememberSportsInteraction(baseScale = 1.02f, label = "sportsPickerRowZoom")
    val shape = tokens.shapes.compactCard
    val quality = remember(item.channel.name) { IptvChannelMerge.qualityLabel(item.channel.name) }
    val meta = listOfNotNull(
        item.playlist.name.takeIf { it.isNotBlank() },
        item.categoryName.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sportsInteractive(
                state = interaction,
                focusRequester = focusRequester,
                onFocused = onFocused,
                onClick = onClick,
            )
            .clip(shape)
            .background(
                if (interaction.highlighted) SolidColor(tokens.colors.focusBackground)
                else SolidColor(tokens.colors.surfaceCard),
            )
            .smoothBorder(
                width = if (interaction.focused) 2.dp else tokens.borders.hairline,
                color = if (interaction.focused) tokens.colors.borderFocus else tokens.colors.borderSubtle,
                shape = shape,
            )
            .padding(horizontal = tokens.spacing.cardPaddingCompact, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(tokens.colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "#${item.channel.num}",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textMuted,
                maxLines = 1,
            )
            item.channel.logo?.takeIf { it.isNotBlank() }?.let { logo ->
                NuvioAsyncImage(
                    model = logo,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = item.channel.name.ifBlank { "Channel ${item.channel.num}" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (interaction.focused) FontWeight.SemiBold else FontWeight.Medium,
                color = tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (quality != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(tokens.colors.surfaceElevated)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = quality,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ScopeChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean = false,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val interaction = rememberSportsInteraction(baseScale = 1.02f, label = "sportsScopeRowZoom")
    val shape = tokens.shapes.compactCard
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sportsInteractive(
                state = interaction,
                focusRequester = focusRequester,
                onFocused = onFocused,
                onClick = onClick,
            )
            .clip(shape)
            .background(
                if (interaction.highlighted) SolidColor(tokens.colors.focusBackground)
                else SolidColor(tokens.colors.surfaceCard),
            )
            .smoothBorder(
                width = if (interaction.focused) 2.dp else tokens.borders.hairline,
                color = when {
                    interaction.focused -> tokens.colors.borderFocus
                    selected -> tokens.colors.borderSelected
                    else -> tokens.colors.borderSubtle
                },
                shape = shape,
            )
            .padding(horizontal = tokens.spacing.cardPaddingCompact, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected || interaction.focused) FontWeight.SemiBold else FontWeight.Medium,
                color = tokens.colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textMuted,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "›",
            style = MaterialTheme.typography.titleMedium,
            color = if (interaction.highlighted) tokens.colors.accent else tokens.colors.textMuted,
        )
    }
}
