package com.nuvio.app.features.player

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerBrowsePipSportsTest {
    @AfterTest
    fun reset() {
        PlayerBrowsePip.setActive(false)
        PlayerBrowsePip.setSportsPlayerIdentity(null, null)
    }

    @Test
    fun matchingGameAndSourceRequestsExpandTheExistingPlayer() {
        PlayerBrowsePip.setSportsPlayerIdentity("espn:basketball/nba:game-1", "iptv:playlist-1:channel-7")
        PlayerBrowsePip.setActive(true)
        val before = PlayerBrowsePip.expandRequests.value

        val expanded = PlayerBrowsePip.expandSportsPlayerIfMatching(
            launch(gameId = "espn:basketball/nba:game-1", sourceKey = "iptv:playlist-1:channel-7"),
        )

        assertTrue(expanded)
        assertEquals(before + 1, PlayerBrowsePip.expandRequests.value)
    }

    @Test
    fun differentGameOrSourceDoesNotExpandTheExistingPlayer() {
        PlayerBrowsePip.setSportsPlayerIdentity("espn:basketball/nba:game-1", "iptv:playlist-1:channel-7")
        PlayerBrowsePip.setActive(true)
        val before = PlayerBrowsePip.expandRequests.value

        assertFalse(
            PlayerBrowsePip.expandSportsPlayerIfMatching(
                launch(gameId = "espn:basketball/nba:game-2", sourceKey = "iptv:playlist-1:channel-7"),
            ),
        )
        assertFalse(
            PlayerBrowsePip.expandSportsPlayerIfMatching(
                launch(gameId = "espn:basketball/nba:game-1", sourceKey = "iptv:playlist-1:channel-8"),
            ),
        )
        assertEquals(before, PlayerBrowsePip.expandRequests.value)
    }

    private fun launch(gameId: String, sourceKey: String) = PlayerLaunch(
        title = "Away @ Home",
        sourceUrl = "https://example.invalid/live.m3u8",
        streamTitle = "Sports",
        providerName = "IPTV",
        parentMetaId = gameId,
        parentMetaType = "sports",
        sourceIdentityKey = sourceKey,
        sportsEventId = gameId,
    )
}
