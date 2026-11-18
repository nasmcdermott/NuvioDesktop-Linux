package com.nuvio.app.features.sports

import co.touchlab.kermit.Logger
import com.nuvio.app.core.build.AppFeaturePolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything the Sports tab keeps for one profile.
 *
 * Stored as `nuvio_sports.properties` under the account folder, which is what makes Settings >
 * Backup carry it for every profile with no work: that backup globs every `*.properties` store and
 * Sports holds no credentials, so the credential-free pass has nothing to strip.
 */
@Serializable
data class SportsSettings(
    /** Settings > Sports > Show in navigation. Off removes the tab without touching anything else. */
    val enabled: Boolean = true,
    /** "" = take the region from the system, as the league catalog's defaults describe. */
    val region: String = "",
    /** Hides scores on the cards, scoreboard and tables; the page keeps its shape. */
    val hideScores: Boolean = false,
    /** Hide games without a current IPTV match, except games involving followed teams. */
    val onlyGamesOnMyChannels: Boolean = false,
    /** Leagues taken off the page entirely. */
    val hiddenLeagues: Set<String> = emptySet(),
    /**
     * A hand-picked channel for one game: event id -> channel key. Outranks anything the matcher
     * infers, because the user can see their own playlist and the matcher cannot.
     */
    val picks: Map<String, String> = emptyMap(),
    /** "leagueId:teamId" -> channel key: use this channel for every game of that team. */
    val teamChannels: Map<String, String> = emptyMap(),
    /** Normalized network key ("espn plus", "tnt") -> channel key: use this channel for that broadcaster. */
    val networkChannels: Map<String, String> = emptyMap(),
    /** leagueId -> channel key: use this channel for every event of that competition (e.g. Formula 1). */
    val leagueChannels: Map<String, String> = emptyMap(),
    /** "eventId|channelKey" the user said is NOT showing that game. */
    val blocked: Set<String> = emptySet(),
    /**
     * Preferred / default IPTV playlist id for Sports matching and playback. "" searches every
     * playlist in Live TV order; when set, channels from that IPTV service lead.
     */
    val preferredPlaylistId: String = "",
    /**
     * Preferred channel region / language filter on game pages: "" = all regions, "EN" = English,
     * "UK" / "US" / "CA" / "IE" / "AU", or "lang:es" etc.
     */
    val channelRegion: String = "",
    /** Ranks 4K / FHD streams ahead of SD and backup copies within the same match tier. */
    val bestQualityFirst: Boolean = true,
    /** Teams the viewer follows, as "leagueId:teamId" — see [SportsFollows.teamKey]. */
    val followedTeams: Set<String> = emptySet(),
    /** Leagues the viewer follows. A followed league shows all of its games, teams followed or not. */
    val followedLeagues: Set<String> = emptySet(),
    /**
     * Whether the follow picker has been offered once. Kept apart from "is anything followed" so
     * that someone who deliberately followed nothing is not asked again on every launch.
     */
    val setUp: Boolean = false,
) {
    /** Total number of manual channel overrides across games, teams, networks and competitions. */
    val totalPickCount: Int
        get() = picks.size + teamChannels.size + networkChannels.size + leagueChannels.size
}

private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

/**
 * Where the Sports settings live. An expect/actual for the same reason the IPTV store is one: the
 * desktop storage directory is `internal` to desktopMain and the store is plain JVM code.
 */
internal expect object SportsSettingsStorage {
    fun loadPayload(): String?
    fun savePayload(payload: String)
}

internal object SportsSettingsRepository {
    private val log = Logger.withTag("SportsSettings")

    private val _settings by lazy {
        MutableStateFlow(
            runCatching {
                SportsSettingsStorage.loadPayload()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { json.decodeFromString(SportsSettings.serializer(), it) }
            }.onFailure { log.w { "Unreadable sports settings, starting fresh: ${it.message}" } }
                .getOrNull() ?: SportsSettings(),
        )
    }

    val settings: StateFlow<SportsSettings> get() = _settings.asStateFlow()

    fun setEnabled(enabled: Boolean) = mutate { it.copy(enabled = enabled) }

    fun setRegion(region: String) = mutate { it.copy(region = region.trim().uppercase()) }

    fun setHideScores(hide: Boolean) = mutate { it.copy(hideScores = hide) }

    fun setOnlyGamesOnMyChannels(enabled: Boolean) = mutate { it.copy(onlyGamesOnMyChannels = enabled) }

    fun setLeagueHidden(leagueId: String, hidden: Boolean) = mutate { current ->
        current.copy(hiddenLeagues = if (hidden) current.hiddenLeagues + leagueId else current.hiddenLeagues - leagueId)
    }

    fun setPick(eventId: String, channelKey: String?) = mutate { current ->
        current.copy(picks = if (channelKey == null) current.picks - eventId else current.picks + (eventId to channelKey))
    }

    fun setTeamChannel(teamKey: String, channelKey: String?) = mutate { current ->
        current.copy(
            teamChannels = if (channelKey == null) current.teamChannels - teamKey else current.teamChannels + (teamKey to channelKey),
        )
    }

    fun setNetworkChannel(networkKey: String, channelKey: String?) = mutate { current ->
        val clean = SportsChannelMatcher.networkKey(networkKey)
        if (clean.isEmpty()) return@mutate current
        current.copy(
            networkChannels = if (channelKey == null) current.networkChannels - clean else current.networkChannels + (clean to channelKey),
        )
    }

    fun setLeagueChannel(leagueId: String, channelKey: String?) = mutate { current ->
        current.copy(
            leagueChannels = if (channelKey == null) current.leagueChannels - leagueId else current.leagueChannels + (leagueId to channelKey),
        )
    }

    fun setPreferredPlaylistId(playlistId: String) = mutate { current ->
        current.copy(preferredPlaylistId = playlistId.trim())
    }

    fun setChannelRegion(regionCode: String) = mutate { current ->
        current.copy(channelRegion = regionCode.trim())
    }

    fun setBestQualityFirst(enabled: Boolean) = mutate { current ->
        current.copy(bestQualityFirst = enabled)
    }

    fun blockChannel(eventId: String, channelKey: String) = mutate { current ->
        current.copy(blocked = current.blocked + "${eventId}|$channelKey")
    }

    fun unblockChannel(eventId: String, channelKey: String) = mutate { current ->
        current.copy(blocked = current.blocked - "${eventId}|$channelKey")
    }

    fun clearPicks() = mutate {
        it.copy(
            picks = emptyMap(),
            teamChannels = emptyMap(),
            networkChannels = emptyMap(),
            leagueChannels = emptyMap(),
            blocked = emptySet(),
        )
    }

    fun setTeamFollowed(teamKey: String, followed: Boolean) = mutate { current ->
        current.copy(
            followedTeams = if (followed) current.followedTeams + teamKey else current.followedTeams - teamKey,
        )
    }

    fun setLeagueFollowed(leagueId: String, followed: Boolean) = mutate { current ->
        current.copy(
            followedLeagues = if (followed) current.followedLeagues + leagueId else current.followedLeagues - leagueId,
        )
    }

    /** Marks the picker as offered, so it never opens by itself again. */
    fun markSetUp() {
        if (_settings.value.setUp) return
        mutate { it.copy(setUp = true) }
    }

    fun clearFollows() = mutate { it.copy(followedTeams = emptySet(), followedLeagues = emptySet()) }

    fun replaceForBackup(settings: SportsSettings) {
        _settings.value = settings
        persist()
    }

    private fun mutate(change: (SportsSettings) -> SportsSettings) {
        _settings.update(change)
        persist()
    }

    private fun persist() {
        // A build that does not carry the feature must not write its store, so an ordinary build
        // never leaves a file behind that a Sports build would then half-read.
        if (!AppFeaturePolicy.sportsEnabled) return
        runCatching { SportsSettingsStorage.savePayload(json.encodeToString(SportsSettings.serializer(), _settings.value)) }
            .onFailure { log.e(it) { "Failed to save sports settings" } }
    }
}

/** The store the desktop actual reads and writes. Named here so the backup and the UI agree. */
internal const val SPORTS_STORE_NAME = "nuvio_sports"
