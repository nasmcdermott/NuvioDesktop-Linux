package com.nuvio.app.features.sports

import com.nuvio.app.features.iptv.IptvChannel
import com.nuvio.app.features.iptv.IptvEpgEntry
import com.nuvio.app.features.iptv.XmltvGuide
import java.text.Normalizer

/**
 * Finds which of the user's channels show a game.
 *
 * A channel's name alone is a poor clue — providers rename channels constantly, and an event
 * channel may be called "NFL 03" one month and something else the next. The playlist's own guide is
 * far better evidence, so strategies are ranked and the best evidence wins:
 *
 *  1. [Rank.Guide] — a programme around kick-off naming both teams.
 *  2. [Rank.GuideNetwork] — the network the feed lists (ESPN, TNT SPORT 2) with a sports programme
 *     on at kick-off. This is what makes a game box office event on a generic channel findable.
 *  3. [Rank.ChannelName] — the channel's own name names the teams or the league.
 *  4. [Rank.NetworkName] — the channel's own name contains the network.
 *
 * Everything here is pure: it takes the games, the channels and the guide it is given, so it can be
 * tested without a network, a disk or Compose. User overrides are applied *before* this in
 * [SportsChannelPicker], because a person's choice outranks any inference.
 */
internal object SportsChannelMatcher {

    /** Strongest evidence first; [SportsChannelMatch.rank] sorts by it. */
    enum class Rank(val order: Int) {
        Guide(0),
        GuideNetwork(1),
        ChannelName(2),
        NetworkName(3),
    }

    data class SportsChannelMatch(
        val channel: IptvChannel,
        val rank: Rank,
        /** Why this channel was chosen, for the "why is this here?" line on the game page. */
        val reason: String,
    )

    /** How far around kick-off a guide programme may start and still be "this game". */
    private const val GUIDE_WINDOW_BEFORE_MS = 3 * 60 * 60 * 1000L
    private const val GUIDE_WINDOW_AFTER_MS = 3 * 60 * 60 * 1000L

    /**
     * Channels showing [event], best first.
     *
     * [channels] should already be the ones the user can see (their playlists, minus hidden and
     * locked groups); this does not know about any of that.
     */
    fun match(
        event: SportsEvent,
        league: League?,
        channels: List<IptvChannel>,
        guide: XmltvGuide?,
    ): List<SportsChannelMatch> {
        if (channels.isEmpty()) return emptyList()
        val away = event.away.team
        val home = event.home.team
        val teamTerms = listOf(teamTerms(away), teamTerms(home))
        val leagueTerms = league?.let { SportsCatalog.searchWords(it) }.orEmpty().map { normalize(it) }
            .filter { it.isNotEmpty() }
        val networkTerms = event.network?.let { listOf(normalize(it)) }?.filter { it.isNotEmpty() }.orEmpty()

        val matches = ArrayList<SportsChannelMatch>(4)
        for (channel in channels) {
            val channelName = " ${normalize(channel.name)} "

            // 1. The guide, around kick-off, naming both teams.
            val programme = guideProgrammeAround(guide, channel, event.startMs)
            if (programme != null && namesBothTeams(programme.title, teamTerms)) {
                matches += SportsChannelMatch(channel, Rank.Guide, programme.title)
                continue
            }

            // 2. The feed's network, with a sports programme on at kick-off.
            if (networkTerms.isNotEmpty() && programme != null &&
                networkTerms.any { " $it " in " ${normalize(programme.title)} " } &&
                looksLikeSport(programme, leagueTerms)
            ) {
                matches += SportsChannelMatch(channel, Rank.GuideNetwork, programme.title)
                continue
            }

            // 3. The channel's own name carries both teams (event channels: "NFL 03: Bears @ Lions").
            if (namesBothTeams(channel.name, teamTerms)) {
                matches += SportsChannelMatch(channel, Rank.ChannelName, channel.name)
                continue
            }

            // 4. The channel's own name carries the network.
            if (networkTerms.isNotEmpty() && networkTerms.any { " $it " in channelName }) {
                matches += SportsChannelMatch(channel, Rank.NetworkName, channel.name)
            }
        }

        // Stable within a rank, so the playlist's own order decides ties rather than the hash of a name.
        return matches.sortedBy { it.rank.order }
    }

    /**
     * The programme showing on [channel] at [atMs], or null. Prefers one that has already started,
     * since a broadcast starting a minute late is still the game.
     */
    private fun guideProgrammeAround(guide: XmltvGuide?, channel: IptvChannel, atMs: Long): IptvEpgEntry? {
        val entries = guide?.entriesFor(channel) ?: return null
        if (entries.isEmpty()) return null
        val from = atMs - GUIDE_WINDOW_BEFORE_MS
        val to = atMs + GUIDE_WINDOW_AFTER_MS
        val inWindow = entries.filter { it.startMs in from..to || (it.startMs <= atMs && it.endMs > atMs) }
        if (inWindow.isEmpty()) return null
        return inWindow.minByOrNull { candidate ->
            // An advert-length entry can sit on top of kick-off; prefer real programmes.
            val duration = candidate.endMs - candidate.startMs
            val tooShort = if (duration < 15 * 60 * 1000L) 1 else 0
            val offKickoff = kotlin.math.abs(candidate.startMs - atMs)
            tooShort.toLong() * 100_000_000_000L + offKickoff
        }
    }

    /** True when [text] names every team in [teamTerms] (each side matched by any of its own names). */
    private fun namesBothTeams(text: String, teamTerms: List<List<String>>): Boolean {
        val normalized = " ${normalize(text)} "
        if (normalized.isBlank() || teamTerms.size < 2) return false
        return teamTerms.all { side -> side.any { it.isNotEmpty() && " $it " in normalized } }
    }

    private fun looksLikeSport(programme: IptvEpgEntry, leagueTerms: List<String>): Boolean {
        val title = " ${normalize(programme.title)} "
        if (leagueTerms.any { " $it " in title }) return true
        return programme.kind?.let { normalize(it) } in SPORT_KINDS
    }

    /** Every name a team can be recognised by, normalised: "East Carolina", "E Carolina", "ECU". */
    private fun teamTerms(team: Team): List<String> = buildList {
        add(normalize(team.name))
        add(normalize(team.shortName))
        add(normalize(team.abbreviation))
        // "East Carolina" is often listed as "E Carolina" in a guide's short title.
        initials(team.name)?.let { add(it) }
    }.filter { it.length >= 2 }.distinct()

    /** "East Carolina" -> "ecarolina" and "ec", so a guide's terse spelling still matches. */
    private fun initials(name: String): String? {
        val words = name.split(' ').filter { it.isNotBlank() }
        if (words.size < 2) return null
        return words.joinToString("") { it.first().lowercaseChar().toString() } + normalize(words.last())
    }

    private val SPORT_KINDS = setOf("sport", "sports", "football", "basketball", "hockey", "baseball", "soccer", "live sports")

    /**
     * Lowercased, accents removed, punctuation collapsed: the same spelling differences
     * [XmltvGuide.normalizeName] handles, plus the `&` providers write as "and".
     *
     * Words are kept apart by a single space rather than squashed together, so "la liga" does not
     * match a channel called "alaliga" — a false positive on a channel name is a wrong channel on
     * screen, which is worse than a missed match the user can fix by picking one.
     */
    internal fun normalize(text: String): String {
        val plain = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .replace("&", " and ")
            // Apostrophes vanish rather than splitting a word: "M&M's" is one token, not three.
            .replace(Regex("""['\u2019\u02BC]"""), "")
        return plain.replace(Regex("""[^a-z0-9]+"""), " ").trim()
    }
}
