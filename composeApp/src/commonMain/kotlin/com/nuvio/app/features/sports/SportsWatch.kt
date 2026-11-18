package com.nuvio.app.features.sports

import com.nuvio.app.features.iptv.IptvCatchup
import com.nuvio.app.features.iptv.IptvChannel
import com.nuvio.app.features.iptv.IptvChannelMerge
import com.nuvio.app.features.iptv.IptvEpgEntry
import com.nuvio.app.features.iptv.IptvMergedChannel
import com.nuvio.app.features.iptv.IptvPlaybackRequests
import com.nuvio.app.features.iptv.IptvPlaylist

/**
 * Matching a game to the user's IPTV channels and launching playback.
 *
 * Watching a matched sports game routes through [IptvPlaybackRequests], bringing stream format
 * failover (`ts` ⇄ `m3u8`), duplicate-channel backup failover ([Option.backups]), Stalker portal
 * link renewal, proxy relay, EPG catch-up ("Watch from start") and Channel Up/Down zapping across
 * the game's matched feeds.
 */
internal object SportsWatch {

    enum class PickScope { EVENT, TEAM, LEAGUE, NETWORK }

    /**
     * One playable channel group offered for a game: [channel] is the primary best-quality stream
     * and [backups] are duplicate copies (HD/SD/Backup) in the same playlist tried automatically on
     * stream failure.
     */
    data class Option(
        val playlist: IptvPlaylist,
        val channel: IptvChannel,
        val reason: String,
        val backups: List<IptvChannel> = emptyList(),
        val categoryName: String = "",
        val programme: IptvEpgEntry? = null,
        val quality: String? = null,
        val regionCode: String? = null,
        val isManualPick: Boolean = false,
        val pickScope: PickScope? = null,
        val canCatchup: Boolean = false,
    ) {
        val mergedChannel: IptvMergedChannel
            get() = IptvMergedChannel(listOf(channel) + backups)
    }

    /**
     * All playable channel options for [event], ordered best first:
     *  1. Manual overrides (for this game, either team, the competition, or a broadcast network),
     *  2. Automatic matches from EPG guides, fixture channel names, and broadcast networks, ranked
     *     by preferred IPTV service, preferred/home region, match confidence, and stream quality,
     *     with FHD/HD/SD copies of the same channel folded into [Option.backups].
     */
    fun optionsFor(
        event: SportsEvent,
        settings: SportsSettings,
        nowMs: Long = System.currentTimeMillis(),
    ): List<Option> {
        val explicitKeys = collectExplicitKeys(event, settings)
        val available = SportsChannels.available(
            preferredPlaylistId = settings.preferredPlaylistId,
            explicitKeys = explicitKeys,
        )
        if (available.isEmpty()) return emptyList()

        val usable = available.filterNot { item ->
            isBlocked(settings.blocked, event.id, item)
        }
        if (usable.isEmpty()) return emptyList()

        val league = SportsCatalog.league(event.leagueId)
        val effectiveRegion = settings.channelRegion.ifBlank {
            SportsChannels.homeRegion(settings)?.let { if (it == "GB") "UK" else it }.orEmpty()
        }

        // 1. Resolve manual overrides first (event > team > league > network), keeping automatic
        //    matches underneath as backup options rather than hiding them.
        val manualOptions = resolveManualOptions(event, league, settings, usable, nowMs)
        val manualKeys = manualOptions.flatMapTo(HashSet()) { opt ->
            listOf("${opt.playlist.id}::${opt.channel.key}") + opt.backups.map { "${opt.playlist.id}::${it.key}" }
        }

        // 2. Run automatic matching per playlist (preserving preferred playlist order).
        val autoRaw = ArrayList<Option>()
        val byPlaylist = LinkedHashMap<String, MutableList<SportsChannels.Available>>()
        for (item in usable) {
            if ("${item.playlist.id}::${item.channel.key}" in manualKeys) continue
            byPlaylist.getOrPut(item.playlist.id) { ArrayList() } += item
        }

        for ((playlistId, entries) in byPlaylist) {
            val playlist = entries.first().playlist
            val guide = SportsChannels.guide(playlistId)
            val catNames = HashMap<String, String>()
            val catRegions = HashMap<String, String>()
            val byChannelUid = HashMap<Int, SportsChannels.Available>(entries.size)
            for (entry in entries) {
                byChannelUid[entry.channel.uid] = entry
                if (entry.categoryName.isNotEmpty()) catNames[entry.channel.categoryId] = entry.categoryName
                if (entry.categoryRegion != null) catRegions[entry.channel.categoryId] = entry.categoryRegion
            }

            val matches = SportsChannelMatcher.match(
                event = event,
                league = league,
                channels = entries.map { it.channel },
                guide = guide,
                categoryNames = catNames,
                categoryRegions = catRegions,
                bestQualityFirst = settings.bestQualityFirst,
                preferredRegion = effectiveRegion,
            )

            // Fold FHD/HD/SD copies of the same channel within one playlist into primary + backups.
            val grouped = groupPlaylistMatches(matches, catRegions, settings.bestQualityFirst)
            for (group in grouped) {
                val lead = group.first()
                val backups = group.drop(1).map { it.channel }
                val meta = byChannelUid[lead.channel.uid]
                val merged = IptvMergedChannel(listOf(lead.channel) + backups)
                val catchupProg = lead.programme ?: syntheticCatchupEntry(event)
                val canReplay = catchupProg != null &&
                    (event.state == GameState.LIVE || event.state == GameState.POST) &&
                    IptvCatchup.available(playlist, merged, catchupProg, nowMs)
                autoRaw += Option(
                    playlist = playlist,
                    channel = lead.channel,
                    reason = lead.reason,
                    backups = backups,
                    categoryName = meta?.categoryName.orEmpty(),
                    programme = lead.programme,
                    quality = lead.quality ?: IptvChannelMerge.qualityLabel(lead.channel.name),
                    regionCode = lead.regionCode,
                    isManualPick = false,
                    pickScope = null,
                    canCatchup = canReplay,
                )
            }
        }

        // Cross-playlist sort: preferred playlist first when chosen, else best match rank + region + quality.
        val preferredId = settings.preferredPlaylistId
        val sortedAuto = autoRaw.sortedWith(
            compareByDescending<Option> { preferredId.isNotBlank() && it.playlist.id == preferredId }
                .thenByDescending {
                    effectiveRegion.isNotBlank() && SportsChannelRegions.includes(it.regionCode, effectiveRegion)
                }
                .thenBy { rankBucket(it.reason) }
                .thenByDescending { if (settings.bestQualityFirst) IptvChannelMerge.rank(it.channel.name) else 0 },
        )

        return manualOptions + sortedAuto
    }

    /** Completed games may expose only a genuine catch-up/replay, never a live channel source. */
    fun choicesFor(event: SportsEvent, options: List<Option>): List<Option> =
        if (event.state == GameState.POST) options.filter { it.canCatchup } else options

    /** Cards never imply a completed game's broadcast is still live; show only real replay options. */
    fun cardChannelLabel(event: SportsEvent, matchedChannelName: String?): String? =
        if (event.state == GameState.POST) {
            matchedChannelName?.let { "⏮ $it replay" }
        } else {
            matchedChannelName?.let { "▶ $it" } ?: event.network
        }

    /** Backwards-compatible overload for callers passing explicit `pickedChannelKey` and `blocked`. */
    fun optionsFor(
        event: SportsEvent,
        pickedChannelKey: String? = null,
        blocked: Set<String> = emptySet(),
    ): List<Option> {
        val current = SportsSettingsRepository.settings.value
        val adjusted = current.copy(
            picks = if (pickedChannelKey != null) current.picks + (event.id to pickedChannelKey) else current.picks,
            blocked = blocked,
        )
        return optionsFor(event, adjusted)
    }

    /** The key [SportsSettings.blocked] stores for "this channel is not showing this game". */
    fun blockKey(eventId: String, channelKey: String): String = "$eventId|$channelKey"

    /** True when [channelKey] (or `playlistId::channelKey`) is marked wrong for [eventId]. */
    fun isBlocked(blocked: Set<String>, eventId: String, item: SportsChannels.Available): Boolean =
        blocked.contains(blockKey(eventId, item.channel.key)) ||
            blocked.contains(blockKey(eventId, item.refKey))

    /**
     * Plays [option], passing its duplicate [Option.backups] as automatic stream failover alternates
     * and the playlist's other matched options for this game as the Channel Up/Down zap list.
     */
    fun play(option: Option, allOptionsForGame: List<Option> = emptyList()): Boolean {
        val samePlaylistZap = allOptionsForGame
            .filter { it.playlist.id == option.playlist.id }
            .map { it.channel }
            .distinctBy { it.uid }
        val zapList = if (samePlaylistZap.size >= 2) samePlaylistZap else emptyList()
        val alternates = if (option.backups.isNotEmpty()) {
            mapOf(option.channel.uid to option.backups)
        } else {
            emptyMap()
        }
        return IptvPlaybackRequests.play(
            playlist = option.playlist,
            channel = option.channel,
            zapList = zapList,
            alternates = alternates,
        )
    }

    /**
     * Plays [event] from kick-off via provider catch-up/timeshift when [Option.canCatchup] is true.
     */
    fun playFromStart(event: SportsEvent, option: Option, allOptionsForGame: List<Option> = emptyList()): Boolean {
        val entry = option.programme ?: syntheticCatchupEntry(event) ?: return false
        val samePlaylistZap = allOptionsForGame
            .filter { it.playlist.id == option.playlist.id }
            .map { it.channel }
            .distinctBy { it.uid }
        val zapList = if (samePlaylistZap.size >= 2) samePlaylistZap else emptyList()
        val alternates = if (option.backups.isNotEmpty()) {
            mapOf(option.channel.uid to option.backups)
        } else {
            emptyMap()
        }
        return IptvPlaybackRequests.playCatchup(
            playlist = option.playlist,
            channel = option.mergedChannel,
            entry = entry,
            zapList = zapList,
            alternates = alternates,
        )
    }

    /**
     * Plays the best matched channel for [event] (using Catch-up when the game has finished and
     * catch-up is available). Returns false when nothing matches on the user's playlists.
     */
    fun playBest(event: SportsEvent, settings: SportsSettings = SportsSettingsRepository.settings.value): Boolean {
        val options = optionsFor(event, settings)
        val best = options.firstOrNull() ?: return false
        if (event.state == GameState.POST) {
            val replayable = options.firstOrNull { it.canCatchup } ?: return false
            return playFromStart(event, replayable, options)
        }
        return play(best, options)
    }

    fun playBest(
        event: SportsEvent,
        pickedChannelKey: String?,
        blocked: Set<String> = emptySet(),
    ): Boolean {
        val options = optionsFor(event, pickedChannelKey, blocked)
        val best = options.firstOrNull() ?: return false
        return play(best, options)
    }

    // region Internal helpers

    private fun collectExplicitKeys(event: SportsEvent, settings: SportsSettings): Set<String> = buildSet {
        settings.picks[event.id]?.let(::add)
        settings.teamChannels[SportsFollows.teamKey(event.leagueId, event.away.team.id)]?.let(::add)
        settings.teamChannels[SportsFollows.teamKey(event.leagueId, event.home.team.id)]?.let(::add)
        settings.leagueChannels[event.leagueId]?.let(::add)
        event.allNetworks.forEach { net ->
            settings.networkChannels[SportsChannelMatcher.networkKey(net)]?.let(::add)
        }
    }

    private fun resolveManualOptions(
        event: SportsEvent,
        league: League?,
        settings: SportsSettings,
        usable: List<SportsChannels.Available>,
        nowMs: Long,
    ): List<Option> {
        data class CandidatePick(val savedKey: String, val scope: PickScope, val reason: String)

        val candidates = buildList {
            settings.picks[event.id]?.let { add(CandidatePick(it, PickScope.EVENT, "Your pick for this game")) }
            val awayKey = SportsFollows.teamKey(event.leagueId, event.away.team.id)
            settings.teamChannels[awayKey]?.let {
                add(CandidatePick(it, PickScope.TEAM, "Your channel for ${event.away.team.shortName.ifBlank { event.away.team.name }}"))
            }
            val homeKey = SportsFollows.teamKey(event.leagueId, event.home.team.id)
            settings.teamChannels[homeKey]?.let {
                add(CandidatePick(it, PickScope.TEAM, "Your channel for ${event.home.team.shortName.ifBlank { event.home.team.name }}"))
            }
            settings.leagueChannels[event.leagueId]?.let {
                add(CandidatePick(it, PickScope.LEAGUE, "Your channel for ${league?.name ?: "this competition"}"))
            }
            for (net in event.allNetworks) {
                val netKey = SportsChannelMatcher.networkKey(net)
                settings.networkChannels[netKey]?.let {
                    add(CandidatePick(it, PickScope.NETWORK, "Your channel for $net"))
                }
            }
        }
        if (candidates.isEmpty()) return emptyList()

        val seen = HashSet<String>()
        val out = ArrayList<Option>(candidates.size)
        for (cand in candidates) {
            val resolved = SportsChannels.resolvePick(cand.savedKey, usable) ?: continue
            if (!seen.add(resolved.refKey)) continue
            val guide = SportsChannels.guide(resolved.playlist.id)
            val prog = SportsChannelMatcher.guideProgrammeAround(guide, resolved.channel, event.startMs)
            // Also attach any other copies of the picked channel in that playlist as failover backups!
            val mergeKey = IptvChannelMerge.mergeKey(resolved.channel.name, resolved.categoryRegion)
            val backups = if (mergeKey == null) {
                emptyList()
            } else {
                usable.asSequence()
                    .filter {
                        it.playlist.id == resolved.playlist.id &&
                            it.channel.uid != resolved.channel.uid &&
                            IptvChannelMerge.mergeKey(it.channel.name, it.categoryRegion) == mergeKey
                    }
                    .sortedByDescending { IptvChannelMerge.rank(it.channel.name) }
                    .map { it.channel }
                    .toList()
            }
            val merged = IptvMergedChannel(listOf(resolved.channel) + backups)
            val catchupEntry = prog ?: syntheticCatchupEntry(event)
            val canReplay = catchupEntry != null &&
                (event.state == GameState.LIVE || event.state == GameState.POST) &&
                IptvCatchup.available(resolved.playlist, merged, catchupEntry, nowMs)
            out += Option(
                playlist = resolved.playlist,
                channel = resolved.channel,
                reason = cand.reason,
                backups = backups,
                categoryName = resolved.categoryName,
                programme = prog,
                quality = IptvChannelMerge.qualityLabel(resolved.channel.name),
                regionCode = SportsChannelMatcher.channelRegionCode(
                    resolved.channel.name,
                    resolved.categoryName,
                    resolved.categoryRegion,
                ),
                isManualPick = true,
                pickScope = cand.scope,
                canCatchup = canReplay,
            )
        }
        return out
    }

    private fun groupPlaylistMatches(
        matches: List<SportsChannelMatcher.SportsChannelMatch>,
        catRegions: Map<String, String>,
        bestQualityFirst: Boolean,
    ): List<List<SportsChannelMatcher.SportsChannelMatch>> {
        if (matches.size < 2) return matches.map { listOf(it) }
        val groups = LinkedHashMap<String, MutableList<SportsChannelMatcher.SportsChannelMatch>>()
        for (match in matches) {
            val mergeKey = IptvChannelMerge.mergeKey(match.channel.name, catRegions[match.channel.categoryId])
                ?: "#${match.channel.uid}"
            groups.getOrPut(mergeKey) { ArrayList(2) } += match
        }
        return groups.values.map { copies ->
            if (copies.size == 1) {
                copies
            } else {
                copies.sortedWith(
                    compareBy<SportsChannelMatcher.SportsChannelMatch> { it.rank.order }
                        .thenByDescending { if (bestQualityFirst) IptvChannelMerge.rank(it.channel.name) else 0 }
                        .thenByDescending { it.score },
                )
            }
        }
    }

    private fun syntheticCatchupEntry(event: SportsEvent): IptvEpgEntry? {
        if (event.startMs <= 0L) return null
        val durationMinutes = SportsCatalog.league(event.leagueId)?.minutes ?: 150
        return IptvEpgEntry(
            title = event.displayTitle,
            startMs = event.startMs,
            endMs = event.startMs + durationMinutes * 60_000L,
            kind = "sport",
        )
    }

    private fun rankBucket(reason: String): Int = when {
        reason.startsWith("Your ") -> 0
        else -> 1
    }

    // endregion
}
