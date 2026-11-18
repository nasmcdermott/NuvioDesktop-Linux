package com.nuvio.app.features.sports

import com.nuvio.app.features.iptv.IptvChannel
import com.nuvio.app.features.iptv.IptvPlaylist
import com.nuvio.app.features.iptv.IptvPlaylistType
import kotlin.test.Test
import kotlin.test.assertEquals

class SportsWatchTest {
    private val playlist = IptvPlaylist(id = "main", type = IptvPlaylistType.M3u, name = "Main")
    private val channel = IptvChannel(uid = 1, key = "espn", num = 1, name = "ESPN", categoryId = "sports")

    private fun option(canCatchup: Boolean) = SportsWatch.Option(
        playlist = playlist,
        channel = channel,
        reason = "ESPN",
        canCatchup = canCatchup,
    )

    private fun event(state: GameState, network: String? = null) = SportsEvent(
        id = "event-1",
        leagueId = "nfl",
        startMs = 1L,
        state = state,
        network = network,
        away = GameSide(Team("away", "Away", "Away", "AWY")),
        home = GameSide(Team("home", "Home", "Home", "HME")),
    )

    @Test
    fun `completed games expose only catch-up replay channels`() {
        val liveChannel = option(canCatchup = false)
        val replayChannel = option(canCatchup = true)

        assertEquals(
            listOf(replayChannel),
            SportsWatch.choicesFor(event(GameState.POST), listOf(liveChannel, replayChannel)),
        )
        assertEquals(emptyList(), SportsWatch.choicesFor(event(GameState.POST), listOf(liveChannel)))
    }

    @Test
    fun `completed cards never label an ordinary match as a live watch channel`() {
        assertEquals(null, SportsWatch.cardChannelLabel(event(GameState.POST, network = "ESPN"), null))
        assertEquals("⏮ ESPN replay", SportsWatch.cardChannelLabel(event(GameState.POST), "ESPN"))
        assertEquals("▶ ESPN", SportsWatch.cardChannelLabel(event(GameState.LIVE), "ESPN"))
        assertEquals("ESPN", SportsWatch.cardChannelLabel(event(GameState.PRE, network = "ESPN"), null))
    }

    @Test
    fun `upcoming and live games retain ordinary channel choices`() {
        val liveChannel = option(canCatchup = false)
        val replayChannel = option(canCatchup = true)
        val choices = listOf(liveChannel, replayChannel)

        assertEquals(choices, SportsWatch.choicesFor(event(GameState.PRE), choices))
        assertEquals(choices, SportsWatch.choicesFor(event(GameState.LIVE), choices))
    }
}
