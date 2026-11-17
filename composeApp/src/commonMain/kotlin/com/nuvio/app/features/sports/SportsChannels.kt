package com.nuvio.app.features.sports

import com.nuvio.app.features.iptv.IptvChannel
import com.nuvio.app.features.iptv.IptvEpgRepository
import com.nuvio.app.features.iptv.IptvPlaylist
import com.nuvio.app.features.iptv.IptvRepository
import com.nuvio.app.features.iptv.XmltvGuide

/**
 * What the Sports page needs from Live TV: the channels the user actually has, and the guide that
 * says what is on them.
 *
 * Kept apart from both so neither side grows a dependency on the other: Sports reads IPTV, IPTV
 * never learns that Sports exists.
 */
internal object SportsChannels {

    /** A channel together with the playlist it came from, since playing it needs both. */
    data class Available(
        val playlist: IptvPlaylist,
        val channel: IptvChannel,
    )

    /** Every channel of every playlist, in playlist order. Empty until the user adds a playlist. */
    fun available(): List<Available> {
        val playlists = IptvRepository.settings.value.playlists
        if (playlists.isEmpty()) return emptyList()
        return playlists.flatMap { playlist ->
            val content = IptvRepository.contentState(playlist.id).value.content ?: return@flatMap emptyList()
            content.channels.map { Available(playlist, it) }
        }
    }

    /** Whether the Sports tab has anything to work with: a playlist with channels loaded. */
    fun hasPlaylists(): Boolean = IptvRepository.settings.value.playlists.isNotEmpty()

    /**
     * The guide for [playlistId], if it has one loaded. Null is normal — a playlist with no XMLTV
     * source still plays, it just leaves the matcher with less to go on.
     */
    fun guide(playlistId: String): XmltvGuide? =
        IptvEpgRepository.guideState(playlistId).value.guide
}
