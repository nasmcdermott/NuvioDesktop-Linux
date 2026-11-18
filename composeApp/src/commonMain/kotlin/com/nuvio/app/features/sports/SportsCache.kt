package com.nuvio.app.features.sports

import co.touchlab.kermit.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

/**
 * Games already fetched, on disk, so the Sports tab opens on the last thing it showed instead of an
 * empty page, and a dead feed leaves the day readable rather than blank.
 *
 * Files live under `sports-cache/` in the account folder, beside the IPTV channel cache and for the
 * same reason: `nuvio_sports.properties` is what Settings > Backup carries, and fetched games are
 * not configuration. Keeping the cache out of `*.properties` is what keeps it out of a backup.
 *
 * Each entry records when it was fetched; [SportsRepository] decides whether that is fresh enough.
 * A cache file that cannot be read is treated as absent, never as an error.
 */
internal object SportsCache {
    private val log = Logger.withTag("SportsCache")
    private val json = Json { ignoreUnknownKeys = true }

    /** Bumped when [CachedGames] changes shape, so old files are ignored rather than misread. */
    private const val CACHE_VERSION = 1

    @Serializable
    private class CachedGames(
        val version: Int,
        val leagueId: String,
        val fromDay: String,
        val toDay: String,
        val fetchedAtMs: Long,
        val games: List<SportsEvent>,
    )

    @Serializable
    private class CachedTeams(
        val version: Int,
        val leagueId: String,
        val fetchedAtMs: Long,
        val teams: List<Team>,
    )

    @Serializable
    private class CachedStandings(
        val version: Int,
        val leagueId: String,
        val fetchedAtMs: Long,
        val rows: List<Standing>,
    )

    private val root: Path get() = SportsCacheFiles.dir()

    private fun file(name: String): Path =
        root.resolve(name.filter { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' })

    data class CachedEvents(val games: List<SportsEvent>, val fetchedAtMs: Long)

    data class CachedTeamList(val teams: List<Team>, val fetchedAtMs: Long)

    data class CachedStandingsList(val rows: List<Standing>, val fetchedAtMs: Long)

    /** A league's games for one date range, or null when nothing usable is cached. */
    @OptIn(ExperimentalSerializationApi::class)
    fun readGames(leagueId: String, fromDay: String, toDay: String): CachedEvents? = runCatching {
        val target = file(gamesName(leagueId, fromDay, toDay))
        if (!target.exists()) return null
        target.inputStream().use { input ->
            json.decodeFromStream(CachedGames.serializer(), GZIPInputStream(input.buffered()))
        }
    }.onFailure { log.w { "Unreadable sports games cache: ${it.message}" } }.getOrNull()
        ?.takeIf { it.version == CACHE_VERSION }
        ?.let { CachedEvents(it.games, it.fetchedAtMs) }

    fun writeGames(leagueId: String, fromDay: String, toDay: String, games: List<SportsEvent>, fetchedAtMs: Long) {
        write(file(gamesName(leagueId, fromDay, toDay))) { out ->
            GZIPOutputStream(out.buffered()).use { gzip ->
                json.encodeToStream(
                    CachedGames.serializer(),
                    CachedGames(CACHE_VERSION, leagueId, fromDay, toDay, fetchedAtMs, games),
                    gzip,
                )
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun readTeams(leagueId: String): CachedTeamList? = runCatching {
        val target = file(teamsName(leagueId))
        if (!target.exists()) return null
        target.inputStream().use { input ->
            json.decodeFromStream(CachedTeams.serializer(), GZIPInputStream(input.buffered()))
        }
    }.onFailure { log.w { "Unreadable sports teams cache: ${it.message}" } }.getOrNull()
        ?.takeIf { it.version == CACHE_VERSION }
        ?.let { CachedTeamList(it.teams, it.fetchedAtMs) }

    fun writeTeams(leagueId: String, teams: List<Team>, fetchedAtMs: Long) {
        write(file(teamsName(leagueId))) { out ->
            GZIPOutputStream(out.buffered()).use { gzip ->
                json.encodeToStream(
                    CachedTeams.serializer(),
                    CachedTeams(CACHE_VERSION, leagueId, fetchedAtMs, teams),
                    gzip,
                )
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun readStandings(leagueId: String): CachedStandingsList? = runCatching {
        val target = file(standingsName(leagueId))
        if (!target.exists()) return null
        target.inputStream().use { input ->
            json.decodeFromStream(CachedStandings.serializer(), GZIPInputStream(input.buffered()))
        }
    }.onFailure { log.w { "Unreadable sports standings cache: ${it.message}" } }.getOrNull()
        ?.takeIf { it.version == CACHE_VERSION }
        ?.let { CachedStandingsList(it.rows, it.fetchedAtMs) }

    fun writeStandings(leagueId: String, rows: List<Standing>, fetchedAtMs: Long) {
        write(file(standingsName(leagueId))) { out ->
            GZIPOutputStream(out.buffered()).use { gzip ->
                json.encodeToStream(
                    CachedStandings.serializer(),
                    CachedStandings(CACHE_VERSION, leagueId, fetchedAtMs, rows),
                    gzip,
                )
            }
        }
    }

    /** Drops every cached game and table; the follow picker's team lists are kept, as they barely change. */
    fun clearGames() {
        runCatching {
            if (!root.exists()) return
            Files.list(root).use { paths ->
                paths.filter {
                    val name = it.fileName.toString()
                    name.startsWith("games_") || name.startsWith("standings_")
                }.forEach { it.deleteIfExists() }
            }
        }.onFailure { log.w { "Failed to clear the sports games cache: ${it.message}" } }
    }

    fun clearAll() {
        runCatching {
            if (!root.exists()) return
            Files.list(root).use { paths -> paths.forEach { it.deleteIfExists() } }
        }.onFailure { log.w { "Failed to clear the sports cache: ${it.message}" } }
    }

    private fun gamesName(leagueId: String, fromDay: String, toDay: String) =
        "games_${leagueId.replace('/', '_')}_${fromDay}_$toDay.json.gz"

    private fun teamsName(leagueId: String) = "teams_${leagueId.replace('/', '_')}.json.gz"

    private fun standingsName(leagueId: String) = "standings_${leagueId.replace('/', '_')}.json.gz"

    /** Through a temp file and an atomic move, so a crash never leaves half a file to be read back. */
    private fun write(target: Path, block: (java.io.OutputStream) -> Unit) {
        runCatching {
            Files.createDirectories(target.parent)
            val tmp = target.resolveSibling("${target.fileName}.tmp")
            tmp.outputStream().use(block)
            runCatching { Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
                .recoverCatching { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING) }
                .getOrThrow()
        }.onFailure { log.w { "Failed to cache sports data: ${it.message}" } }
    }
}
