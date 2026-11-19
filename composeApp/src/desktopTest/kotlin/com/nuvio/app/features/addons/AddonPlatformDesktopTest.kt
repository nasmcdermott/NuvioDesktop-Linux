package com.nuvio.app.features.addons

import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddonPlatformDesktopTest {
    @Test
    fun `ordinary raw responses retain the desktop size cap`() {
        val payload = "x".repeat(TEST_BODY_BYTES)

        val result = readRawResponseBody(
            body = payload.toResponseBody(),
            allowLargeResponse = false,
        )

        assertTrue(result.endsWith(RAW_HTTP_TRUNCATION_MARKER))
        assertTrue(result.length < payload.length)
    }

    @Test
    fun `only a stream resource declaring sports supports a sports event`() {
        val sportId = "espn:football/nfl:401234567"
        val ordinaryAddon = AddonManifest(
            id = "ordinary",
            name = "Ordinary",
            description = "",
            version = "1",
            resources = listOf(AddonResource("stream", listOf("movie", "series"))),
            types = listOf("movie", "series"),
            transportUrl = "https://example.test/manifest.json",
        )
        val sportsAddon = ordinaryAddon.copy(
            id = "sports",
            resources = listOf(AddonResource("stream", listOf("sports"), listOf("espn:"))),
        )

        assertFalse(ordinaryAddon.supportsResource("stream", "sports", sportId))
        assertTrue(sportsAddon.supportsResource("stream", "sports", sportId))
        assertFalse(sportsAddon.supportsResource("stream", "sports", "not-a-sports-id"))
    }

    @Test
    fun `trusted bulk responses can bypass the desktop size cap`() {
        val payload = "x".repeat(TEST_BODY_BYTES)

        val result = readRawResponseBody(
            body = payload.toResponseBody(),
            allowLargeResponse = true,
        )

        assertEquals(payload, result)
        assertFalse(result.endsWith(RAW_HTTP_TRUNCATION_MARKER))
    }

    private companion object {
        const val TEST_BODY_BYTES = 1024 * 1024 + 32
    }
}
