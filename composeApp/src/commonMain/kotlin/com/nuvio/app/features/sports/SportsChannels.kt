package com.nuvio.app.features.sports

import com.nuvio.app.features.iptv.IptvChannel
import com.nuvio.app.features.iptv.IptvChannelKind
import com.nuvio.app.features.iptv.IptvEpgRepository
import com.nuvio.app.features.iptv.IptvPlaylist
import com.nuvio.app.features.iptv.IptvPlaylistContent
import com.nuvio.app.features.iptv.IptvRegions
import com.nuvio.app.features.iptv.IptvRepository
import com.nuvio.app.features.iptv.IptvTriageRules
import com.nuvio.app.features.iptv.XmltvGuide

/**
 * What the Sports page needs from Live TV: the channels the user actually has, their category and
 * region metadata, and the guide that says what is on them.
 *
 * Kept apart from both so neither side grows a dependency on the other: Sports reads IPTV, IPTV
 * never learns that Sports exists.
 */
internal object SportsChannels {

    /** A channel together with the playlist and category metadata needed to rank and play it. */
    data class Available(
        val playlist: IptvPlaylist,
        val channel: IptvChannel,
        val categoryName: String = "",
        val categoryRegion: String? = null,
        val kind: IptvChannelKind = IptvChannelKind.Entertainment,
    ) {
        /** Unambiguous reference across multiple playlists, while remaining readable. */
        val refKey: String get() = "${playlist.id}::${channel.key}"
    }

    /**
     * Every usable channel across the user's playlists, with [preferredPlaylistId]'s playlist
     * ordered first when set. Excludes separator banners, adult categories, 24/7 loop categories,
     * and channels dismissed with "Never again" unless explicitly listed in [explicitKeys].
     */
    fun available(
        preferredPlaylistId: String = "",
        explicitKeys: Set<String> = emptySet(),
    ): List<Available> {
        IptvRepository.ensureLoaded()
        val iptv = IptvRepository.settings.value
        val playlists = orderedPlaylists(iptv.playlists, preferredPlaylistId)
        if (playlists.isEmpty()) return emptyList()

        return playlists.flatMap { playlist ->
            val content = IptvRepository.contentState(playlist.id).value.content ?: return@flatMap emptyList()
            val neverKeys = iptv.never[playlist.id].orEmpty()
            buildAvailable(playlist, content).filter { item ->
                if (item.kind == IptvChannelKind.Adult) return@filter false
                val isExplicit = item.channel.key in explicitKeys || item.refKey in explicitKeys
                if (isExplicit) return@filter true
                if (item.channel.key in neverKeys) return@filter false
                if (item.kind == IptvChannelKind.Loops) return@filter false
                !SportsChannelMatcher.isBanner(item.channel.name)
            }
        }
    }

    /**
     * Every non-adult, non-banner channel across all playlists for the manual Channel Picker dialog,
     * so the user can hand-pick any channel even if it sits in an unusual category.
     */
    fun allForPicker(preferredPlaylistId: String = ""): List<Available> {
        IptvRepository.ensureLoaded()
        val playlists = orderedPlaylists(IptvRepository.settings.value.playlists, preferredPlaylistId)
        if (playlists.isEmpty()) return emptyList()
        return playlists.flatMap { playlist ->
            val content = IptvRepository.contentState(playlist.id).value.content ?: return@flatMap emptyList()
            buildAvailable(playlist, content).filter { item ->
                item.kind != IptvChannelKind.Adult && !SportsChannelMatcher.isBanner(item.channel.name)
            }
        }
    }

    /** Effective home country code ("GB", "US", "CA"…) from Live TV or Sports settings, if known. */
    fun homeRegion(sportsSettings: SportsSettings): String? {
        val sportsRegion = sportsSettings.region.trim().uppercase()
        if (sportsRegion.isNotEmpty() && sportsRegion != "OTHER") {
            return if (sportsRegion == "UK") "GB" else sportsRegion
        }
        val iptv = IptvRepository.settings.value
        val firstPlaylistId = iptv.playlists.firstOrNull()?.id
        val triageRegions = firstPlaylistId?.let { iptv.triage[it]?.regions }
        return IptvRegions.effectiveHome(iptv.homeRegion, triageRegions)
    }

    /** Whether the Sports tab has anything to work with: a playlist configured. */
    fun hasPlaylists(): Boolean {
        IptvRepository.ensureLoaded()
        return IptvRepository.settings.value.playlists.isNotEmpty()
    }

    /**
     * The guide for [playlistId], if it has one loaded. Null is normal — a playlist with no XMLTV
     * source still plays, it just leaves the matcher with less to go on.
     */
    fun guide(playlistId: String): XmltvGuide? =
        IptvEpgRepository.guideState(playlistId).value.guide

    /**
     * Resolves a saved pick key (`"playlistId::channelKey"` or legacy `"channelKey"`), falling back
     * to `(playlistId, epgId)` word-similarity matching if the provider renamed the channel.
     */
    internal fun resolvePick(savedKey: String, pool: List<Available>): Available? {
        if (savedKey.isBlank() || pool.isEmpty()) return null
        val hasPlaylistPrefix = "::" in savedKey
        val targetPlaylistId = if (hasPlaylistPrefix) savedKey.substringBefore("::") else null
        val rawChannelKey = if (hasPlaylistPrefix) savedKey.substringAfter("::") else savedKey

        // 1. Exact match (scoped to playlist when present, else any playlist).
        pool.firstOrNull { item ->
            (targetPlaylistId == null || item.playlist.id == targetPlaylistId) &&
                item.channel.key == rawChannelKey
        }?.let { return it }

        // 2. Fallback: if rawChannelKey carried an epgId or recognizable words in the same playlist.
        val scoped = if (targetPlaylistId != null) {
            pool.filter { it.playlist.id == targetPlaylistId }
        } else {
            pool
        }
        val keyTokens = rawChannelKey.lowercase().split(Regex("""[^a-z0-9]+""")).filter { it.length >= 2 }.toSet()
        if (keyTokens.isEmpty()) return null
        return scoped.firstOrNull { item ->
            val epg = item.channel.epgId?.trim()?.lowercase()
            epg != null && epg.length >= 3 && rawChannelKey.lowercase().contains(epg)
        }
    }

    private fun orderedPlaylists(playlists: List<IptvPlaylist>, preferredId: String): List<IptvPlaylist> {
        if (preferredId.isBlank() || playlists.size < 2) return playlists
        val preferred = playlists.firstOrNull { it.id == preferredId } ?: return playlists
        return listOf(preferred) + playlists.filter { it.id != preferredId }
    }

    private fun buildAvailable(playlist: IptvPlaylist, content: IptvPlaylistContent): List<Available> {
        val catNames = content.categoryNames
        val catRegions = IptvRegions.categoryRegions(content)
        return content.channels.map { ch ->
            val catName = catNames[ch.categoryId].takeUnless { it == IptvPlaylistContent.OTHER_CATEGORY }.orEmpty()
            Available(
                playlist = playlist,
                channel = ch,
                categoryName = catName,
                categoryRegion = catRegions[ch.categoryId],
                kind = IptvTriageRules.kindOf(catName),
            )
        }
    }
}
