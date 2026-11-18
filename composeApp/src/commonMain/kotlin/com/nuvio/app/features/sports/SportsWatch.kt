package com.nuvio.app.features.sports

import com.nuvio.app.features.iptv.IptvChannel
import com.nuvio.app.features.iptv.IptvPlaybackRequests
import com.nuvio.app.features.iptv.IptvPlaylist

/**
 * Making a game play.
 *
 * There is no player code here on purpose. A sports game is an ordinary live channel once it has
 * been matched to one, so watching it is the same call the Live TV tab makes: the existing
 * [IptvPlaybackRequests] hands a [com.nuvio.app.features.player.PlayerLaunch] to the app's own
 * player, which brings stream failover, Stalker link renewal, the proxy relay and live MPV options
 * with it.
 */
internal object SportsWatch {

    /** One channel offered for a game: the channel, and the playlist it must be opened from. */
    data class Option(
        val playlist: IptvPlaylist,
        val channel: IptvChannel,
        val reason: String,
    )

    /**
     * The channels showing [event], best first.
     *
     * A user's own pick for this game outranks anything inferred, so [pickedChannelKey] is checked
     * first: if the game was given a channel by hand, that is the answer whatever the matcher says.
     */
    fun optionsFor(
        event: SportsEvent,
        pickedChannelKey: String? = null,
        blocked: Set<String> = emptySet(),
    ): List<Option> {
        val available = SportsChannels.available()
        if (available.isEmpty()) return emptyList()
        val league = SportsCatalog.league(event.leagueId)
        // A wrong match is worse than no match: it plays someone else's game. Anything the user
        // marked wrong for THIS game is dropped before it can be offered again.
        val usable = available.filterNot { blocked.contains(blockKey(event.id, it.channel.key)) }

        // A hand-picked channel may have been renamed or moved between playlists; search every one.
        if (pickedChannelKey != null) {
            usable.firstOrNull { it.channel.key == pickedChannelKey }?.let { picked ->
                return listOf(Option(picked.playlist, picked.channel, "Your pick"))
            }
        }

        val byPlaylist = usable.groupBy { it.playlist.id }
        val options = mutableListOf<Option>()
        for ((playlistId, entries) in byPlaylist) {
            val channels = entries.map { it.channel }
            val guide = SportsChannels.guide(playlistId)
            val matches = SportsChannelMatcher.match(event, league, channels, guide)
            val playlist = entries.first().playlist
            matches.forEach { match ->
                options += Option(playlist, match.channel, match.reason)
            }
        }
        return options
    }

    /** The key [SportsSettings.blocked] stores for "this channel is not showing this game". */
    fun blockKey(eventId: String, channelKey: String): String = "$eventId|$channelKey"

    /** Plays [option]. False when the channel turned out to have no playable URL. */
    fun play(option: Option, zapList: List<IptvChannel> = emptyList()): Boolean =
        IptvPlaybackRequests.play(option.playlist, option.channel, zapList)

    /**
     * Plays the best option for [event]; the boolean is false when nothing is showing it on any of
     * the user's channels.
     */
    fun playBest(
        event: SportsEvent,
        pickedChannelKey: String? = null,
        blocked: Set<String> = emptySet(),
    ): Boolean {
        val best = optionsFor(event, pickedChannelKey, blocked).firstOrNull() ?: return false
        return play(best)
    }
}
