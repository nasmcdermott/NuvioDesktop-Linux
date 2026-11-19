package com.nuvio.app.features.player.desktop

import com.nuvio.app.features.player.PlayerControlsState
import com.nuvio.app.features.player.PlayerMediaSession
import com.nuvio.app.features.player.PlayerMediaSessionState

internal fun linuxMprisTitle(controls: PlayerControlsState, session: PlayerMediaSessionState): String {
    val episode = if (session.episode > 0) {
        val code = "S${session.season.toString().padStart(2, '0')}" +
            "E${session.episode.toString().padStart(2, '0')}"
        listOf(code, controls.pauseOverlay.pauseOverlayEpisodeTitle).filter { it.isNotBlank() }.joinToString(" — ")
    } else controls.episodeText // Already contains the episode title when available.
    return listOf(controls.title, episode).filter { it.isNotBlank() }.joinToString(" — ")
}

/** Adapter for the main player; trailers never acquire a client. Called on the existing UI path. */
internal class LinuxMprisPlayer(
    private val controller: NativePlayerController,
    owner: LinuxMprisSession = LinuxMediaSession.owner,
    private val usable: () -> Boolean,
) : AutoCloseable {
    private val client = owner.client { method, value -> if (usable()) command(method, value) }
    private var token = client.newSource()
    private var state = LinuxMprisState()
    private var seekSerial = 0L
    private var retiringHandle = 0L

    fun sourceChanging(outgoingHandle: Long) {
        retiringHandle = outgoingHandle
        token = client.newSource()
        state = LinuxMprisState(token = token)
    }

    fun update(controls: PlayerControlsState, handle: Long) {
        if (handle == 0L || handle == retiringHandle || !usable()) return
        // At exit the players are closed under the UI (disposeAll); a read then finds no handle.
        val duration: Long
        val position: Long
        val ended: Boolean
        val paused: Boolean
        try {
            duration = NativePlayerBridge.durationMs(handle).coerceIn(0, Long.MAX_VALUE / 1000) * 1000
            position = NativePlayerBridge.positionMs(handle).coerceIn(0, Long.MAX_VALUE / 1000) * 1000
            ended = NativePlayerBridge.isEnded(handle)
            paused = NativePlayerBridge.isPaused(handle)
        } catch (_: IllegalStateException) {
            return
        }
        val session = PlayerMediaSession.current
        state = LinuxMprisState(
            token = token,
            trackId = "/org/mpris/MediaPlayer2/track/t$token",
            title = linuxMprisTitle(controls, session),
            album = controls.title.takeIf { session.episode > 0 || controls.episodeText.isNotBlank() }.orEmpty(),
            artwork = controls.mediaSessionArtwork,
            season = session.season,
            episode = session.episode,
            // Buffering is still intended Playing, like Windows SMTC; mpv pause remains authoritative.
            status = if (ended) "Stopped" else if (paused) "Paused" else "Playing",
            durationUs = duration,
            positionUs = position,
            canSeek = duration > 0 && LinuxMprisNative.seekable(handle),
            canNext = session.canGoNext,
            canPrevious = session.canGoPrevious,
            rate = NativePlayerBridge.speed(handle).toDouble(),
            volume = controller.getVolume()?.let { if (it.isMuted) 0.0 else it.fraction.toDouble() } ?: 1.0,
            seekSerial = seekSerial,
        )
        client.publish(state)
    }

    private fun command(method: String, value: Double) {
        when (method) {
            "Play" -> {
                // Upstream discrete Play does not restart EOF; the toggle action does.
                if (controller.snapshot().isEnded) controller.dispatchKeyboardShortcut("keyboardToggle")
                else controller.dispatchKeyboardShortcut("mediaPlay")
            }
            "Pause" -> controller.dispatchKeyboardShortcut("mediaPause")
            "PlayPause" -> controller.dispatchKeyboardShortcut("keyboardToggle")
            "Next" -> controller.dispatchKeyboardShortcut("mediaNext")
            "Previous" -> controller.dispatchKeyboardShortcut("mediaPrevious")
            "Seek" -> {
                // MPRIS offsets are relative to the current position. Preserve that operation
                // through the controller (including IPTV's cached live-window handling), while
                // retaining the existing end-of-file/next-item behavior.
                linuxMprisRelativeSeekMs(value)?.let { offsetMs ->
                    val positionMs = controller.snapshot().positionMs.coerceAtLeast(0L)
                    val targetMs = linuxMprisSeekTargetMs(positionMs, value)
                    if (targetMs != null && targetMs > state.durationUs / 1_000L) {
                        if (state.canNext) controller.dispatchKeyboardShortcut("mediaNext")
                    } else if (targetMs != null) {
                        controller.seekBy(targetMs - positionMs)
                        seekSerial++
                    }
                }
            }
            "SetPosition" -> if (value >= 0 && value <= state.durationUs) {
                controller.dispatchKeyboardShortcut("scrubFinish", value / 1000.0)
                seekSerial++
            }
            "Volume" -> controller.setVolume(value.toFloat().coerceIn(0f, controller.maxVolumeFraction))
            "Rate" -> controller.dispatchKeyboardShortcut("setPlaybackSpeed", value)
        }
    }
    override fun close() = client.close()
}

/** MPRIS relative Seek uses microseconds; the player controller's relative operation uses millis. */
internal fun linuxMprisRelativeSeekMs(offsetUs: Double): Long? =
    offsetUs.takeIf { it.isFinite() }?.div(1_000.0)?.toLong()

internal fun linuxMprisSeekTargetMs(positionMs: Long, offsetUs: Double): Long? {
    val offsetMs = linuxMprisRelativeSeekMs(offsetUs) ?: return null
    val position = positionMs.coerceAtLeast(0L)
    val target = if (offsetMs > 0 && position > Long.MAX_VALUE - offsetMs) {
        Long.MAX_VALUE
    } else if (offsetMs < 0 && position < Long.MIN_VALUE - offsetMs) {
        Long.MIN_VALUE
    } else {
        position + offsetMs
    }
    return target.coerceAtLeast(0L)
}
