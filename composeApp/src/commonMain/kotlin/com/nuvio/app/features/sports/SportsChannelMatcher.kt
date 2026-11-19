package com.nuvio.app.features.sports

import com.nuvio.app.features.iptv.IptvChannel
import com.nuvio.app.features.iptv.IptvChannelMerge
import com.nuvio.app.features.iptv.IptvEpgEntry
import com.nuvio.app.features.iptv.IptvLanguages
import com.nuvio.app.features.iptv.IptvRegions
import com.nuvio.app.features.iptv.XmltvGuide
import java.text.Normalizer

/**
 * Finds which of the user's IPTV channels show a game, inspired by NuvioPlusIPTV's `SportsMatcher`.
 *
 * Strategies are ranked from strongest evidence to weakest:
 *  1. [Rank.Guide] — an EPG programme around kick-off naming both teams (in title or description)
 *     or the specific event/race/fight card.
 *  2. [Rank.GuideNetwork] — a channel matching one of the broadcast networks with a live sports
 *     programme in the EPG at kick-off (or an EPG programme explicitly naming the network + sport),
 *     excluding channels whose guide shows a different matchup.
 *  3. [Rank.ChannelName] — an event channel whose own name carries both teams ("NFL 03: Bears @ Lions",
 *     "GAME: CHI v DET") or the competition/event ("Sky Sports F1" for Formula 1).
 *  4. [Rank.NetworkName] — a channel whose name matches one of [SportsEvent.allNetworks] via network
 *     aliases ("FS1" ⇄ "Fox Sports 1", "ESPN+" ⇄ "ESPN Plus") while excluding false siblings
 *     ("FOX News" ≠ "FOX", "ESPN2" ≠ "ESPN", "CBS News" ≠ "CBS") and channels whose EPG shows a
 *     non-sport programme or another game at kick-off.
 *
 * Pure Kotlin: takes the event, channels and guide it is given, with no network, disk or Compose.
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
        /** Why this channel was chosen, for the game page subtitle. */
        val reason: String,
        val programme: IptvEpgEntry? = null,
        val score: Int = 0,
        val quality: String? = null,
        val regionCode: String? = null,
    )

    /** How far around kick-off a guide programme may start and still be "this game". */
    private const val GUIDE_WINDOW_BEFORE_MS = 100 * 60 * 1000L
    private const val GUIDE_WINDOW_AFTER_MS = 70 * 60 * 1000L
    private const val MAX_LIVE_PROGRAMME_MS = 6 * 60 * 60 * 1000L

    /**
     * Channels showing [event], best first.
     */
    fun match(
        event: SportsEvent,
        league: League?,
        channels: List<IptvChannel>,
        guide: XmltvGuide?,
        categoryNames: Map<String, String> = emptyMap(),
        categoryRegions: Map<String, String> = emptyMap(),
        bestQualityFirst: Boolean = false,
        preferredRegion: String? = null,
    ): List<SportsChannelMatch> {
        if (channels.isEmpty()) return emptyList()

        val isFieldEvent = event.away.team.id.endsWith(":event") ||
            league?.sport in setOf(Sport.RACING, Sport.GOLF)
        val awayTerms = if (isFieldEvent) emptyList() else strongTeamTerms(event.away.team)
        val awayWeak = if (isFieldEvent) emptyList() else weakTeamTerms(event.away.team)
        val homeTerms = if (isFieldEvent) emptyList() else strongTeamTerms(event.home.team)
        val homeWeak = if (isFieldEvent) emptyList() else weakTeamTerms(event.home.team)
        val abbrAway = normalize(event.away.team.abbreviation).takeIf { it.length in 2..4 }
        val abbrHome = normalize(event.home.team.abbreviation).takeIf { it.length in 2..4 }

        val leagueTerms = league?.let { SportsCatalog.searchWords(it) }
            .orEmpty()
            .map { normalize(expandSymbols(it)) }
            .filter { it.length >= 2 }
            .distinct()
        val eventSpecificTerms = eventWords(event, leagueTerms)
        val networks = event.allNetworks.mapNotNull { raw ->
            val key = networkKey(raw)
            if (key.isEmpty()) null else raw to key
        }.distinctBy { it.second }

        val matches = ArrayList<SportsChannelMatch>(8)
        for (channel in channels) {
            if (isBanner(channel.name)) continue
            val catName = categoryNames[channel.categoryId].orEmpty()
            val catRegion = categoryRegions[channel.categoryId]
            val normChannel = normalize(expandSymbols(channel.name))
            val quality = IptvChannelMerge.qualityLabel(channel.name)
            val regionCode = channelRegionCode(channel.name, catName, catRegion)

            val programme = guideProgrammeAround(guide, channel, event.startMs)
            val validProgramme = programme?.takeUnless { isFillerOrReplay(channel, catName, it) }
            val normProgTitle = validProgramme?.let { normalize(expandSymbols(it.title + " " + it.episode.orEmpty())) }.orEmpty()
            val normProgDesc = validProgramme?.desc?.let { normalize(expandSymbols(it)) }.orEmpty()

            // 1. EPG programme around kick-off naming both teams (or the specific event).
            if (validProgramme != null) {
                val guideScore = if (!isFieldEvent) {
                    scoreTeamMatch(normProgTitle, normProgDesc, awayTerms, awayWeak, homeTerms, homeWeak, abbrAway, abbrHome)
                } else {
                    scoreEventMatch(normProgTitle, normProgDesc, leagueTerms, eventSpecificTerms)
                }
                if (guideScore > 0) {
                    val timingPenalty = if (
                        validProgramme.startMs < event.startMs - 80 * 60_000L ||
                        validProgramme.startMs > event.startMs + 25 * 60_000L
                    ) 8 else 0
                    matches += SportsChannelMatch(
                        channel = channel,
                        rank = Rank.Guide,
                        reason = validProgramme.title,
                        programme = validProgramme,
                        score = (guideScore - timingPenalty).coerceAtLeast(70),
                        quality = quality,
                        regionCode = regionCode,
                    )
                    continue
                }
            }

            // 2. Broadcast network + EPG confirmation at kick-off:
            //    (a) EPG programme itself names the network and looks like live sport, OR
            //    (b) Channel name matches the network AND its EPG shows live sport (not another game).
            val matchedNetwork = networks.firstOrNull { (_, netKey) -> matchesNetworkName(normChannel, netKey) }
            if (validProgramme != null && networks.isNotEmpty()) {
                val epgNamesNetwork = networks.firstOrNull { (_, netKey) ->
                    networkPatterns(netKey).any { hasWord(normProgTitle, it) }
                }
                val sportProgramme = looksLikeSport(validProgramme, normProgTitle, leagueTerms) &&
                    !isOtherGame(normProgTitle, awayTerms, awayWeak, homeTerms, homeWeak, abbrAway, abbrHome)
                if (sportProgramme && (epgNamesNetwork != null || matchedNetwork != null)) {
                    val netLabel = (matchedNetwork ?: epgNamesNetwork)!!.first
                    matches += SportsChannelMatch(
                        channel = channel,
                        rank = Rank.GuideNetwork,
                        reason = validProgramme.title.ifBlank { "$netLabel broadcast" },
                        programme = validProgramme,
                        score = 68,
                        quality = quality,
                        regionCode = regionCode,
                    )
                    continue
                }
            }

            // 3. Event channel whose own name carries both teams ("NFL 03: Bears @ Lions", "GAME: CHI v DET")
            //    or, for field events (F1, Golf), the competition / event name ("Sky Sports F1").
            val nameMatched = if (!isFieldEvent) {
                matchesChannelNameTeams(normChannel, awayTerms, homeTerms, abbrAway, abbrHome)
            } else {
                matchesChannelNameEvent(normChannel, leagueTerms, eventSpecificTerms)
            }
            if (nameMatched) {
                matches += SportsChannelMatch(
                    channel = channel,
                    rank = Rank.ChannelName,
                    reason = channel.name,
                    programme = validProgramme,
                    score = 92,
                    quality = quality,
                    regionCode = regionCode,
                )
                continue
            }

            // 4. Channel named after one of the event's broadcast networks ("US: FOX 5", "Fox Sports 1").
            //    If the channel has an EPG entry right at kick-off that is clearly another matchup or a
            //    non-sport show (movie/news), skip it rather than offering a wrong broadcast.
            if (matchedNetwork != null) {
                val contradictedByGuide = programme != null && (
                    isFillerOrReplay(channel, catName, programme) ||
                        (!isFieldEvent && isOtherGame(normProgTitle, awayTerms, awayWeak, homeTerms, homeWeak, abbrAway, abbrHome)) ||
                        isClearlyNonSport(programme)
                    )
                if (!contradictedByGuide) {
                    matches += SportsChannelMatch(
                        channel = channel,
                        rank = Rank.NetworkName,
                        reason = if (networks.size > 1 || !channel.name.contains(matchedNetwork.first, ignoreCase = true)) {
                            "${matchedNetwork.first} · ${channel.name}"
                        } else {
                            channel.name
                        },
                        programme = validProgramme,
                        score = 48,
                        quality = quality,
                        regionCode = regionCode,
                    )
                }
            }
        }

        // Stable within equal priority so playlist order decides ties when quality/region aren't requested.
        return matches.sortedWith(
            compareBy<SportsChannelMatch> { it.rank.order }
                .thenByDescending { match ->
                    if (preferredRegion.isNullOrBlank()) 0
                    else if (SportsChannelRegions.includes(match.regionCode, preferredRegion)) 1
                    else 0
                }
                .thenByDescending { if (bestQualityFirst) IptvChannelMerge.rank(it.channel.name) else 0 }
                .thenByDescending { it.score },
        )
    }

    /**
     * The programme showing on [channel] around [atMs], or null. Prefers real game-length entries
     * closest to kick-off over short 5-minute filler bumpers.
     */
    internal fun guideProgrammeAround(guide: XmltvGuide?, channel: IptvChannel, atMs: Long): IptvEpgEntry? {
        if (guide == null || atMs <= 0L) return null
        val entries = guide.entriesFor(channel)
        if (entries.isEmpty()) return null
        val from = atMs - GUIDE_WINDOW_BEFORE_MS
        val to = atMs + GUIDE_WINDOW_AFTER_MS
        val inWindow = entries.filter { entry ->
            (entry.startMs in from..to && entry.endMs > atMs + 10 * 60_000L) ||
                (entry.startMs <= atMs + 20 * 60_000L && entry.endMs > atMs + 15 * 60_000L)
        }
        if (inWindow.isEmpty()) return null
        return inWindow.minByOrNull { candidate ->
            val duration = candidate.endMs - candidate.startMs
            val tooShort = if (duration < 20 * 60_000L) 1 else 0
            val offKickoff = kotlin.math.abs(candidate.startMs - atMs)
            tooShort.toLong() * 100_000_000_000L + offKickoff
        }
    }

    private fun scoreTeamMatch(
        normTitle: String,
        normDesc: String,
        awayTerms: List<String>,
        awayWeak: List<String>,
        homeTerms: List<String>,
        homeWeak: List<String>,
        abbrAway: String?,
        abbrHome: String?,
    ): Int {
        if (awayTerms.isEmpty() || homeTerms.isEmpty()) return 0
        val aTitle = awayTerms.any { hasWord(normTitle, it) }
        val hTitle = homeTerms.any { hasWord(normTitle, it) }
        if (aTitle && hTitle) return 100

        if (abbrAway != null && abbrHome != null && matchesAbbreviationPair(normTitle, abbrAway, abbrHome)) {
            return 95
        }

        val aWeakTitle = awayWeak.any { hasWord(normTitle, it) }
        val hWeakTitle = homeWeak.any { hasWord(normTitle, it) }
        if ((aTitle && hWeakTitle) || (hTitle && aWeakTitle)) return 90

        if (normDesc.isNotEmpty()) {
            val aDesc = awayTerms.any { hasWord(normDesc, it) }
            val hDesc = homeTerms.any { hasWord(normDesc, it) }
            if ((aTitle || aDesc) && (hTitle || hDesc)) return 84
        }
        return 0
    }

    private fun scoreEventMatch(
        normTitle: String,
        normDesc: String,
        leagueTerms: List<String>,
        eventTerms: List<String>,
    ): Int {
        val hasLeague = leagueTerms.any { hasWord(normTitle, it) || (normDesc.isNotEmpty() && hasWord(normDesc, it)) }
        val specificHits = eventTerms.count { hasWord(normTitle, it) || (normDesc.isNotEmpty() && hasWord(normDesc, it)) }
        return when {
            hasLeague && specificHits >= 1 -> 94
            specificHits >= 2 -> 82
            leagueTerms.any { it.length >= 4 && it !in GENERIC_SPORT_WORDS && hasWord(normTitle, it) } -> 74
            else -> 0
        }
    }

    private fun matchesChannelNameTeams(
        normChannel: String,
        awayTerms: List<String>,
        homeTerms: List<String>,
        abbrAway: String?,
        abbrHome: String?,
    ): Boolean {
        if (awayTerms.isEmpty() || homeTerms.isEmpty()) return false
        if (awayTerms.any { hasWord(normChannel, it) } && homeTerms.any { hasWord(normChannel, it) }) {
            return true
        }
        // Also accept explicit abbreviation matchups ("CHI v DET", "CHI @ DET", "CHI vs DET")
        // or 3+ letter abbreviations both appearing with a matchup word.
        if (abbrAway != null && abbrHome != null) {
            if (matchesAbbreviationPair(normChannel, abbrAway, abbrHome)) return true
            if (abbrAway.length >= 3 && abbrHome.length >= 3 &&
                abbrAway !in AMBIGUOUS_ABBR && abbrHome !in AMBIGUOUS_ABBR &&
                hasWord(normChannel, abbrAway) && hasWord(normChannel, abbrHome)
            ) {
                return true
            }
        }
        return false
    }

    private fun matchesAbbreviationPair(text: String, a: String, b: String): Boolean {
        val patterns = listOf(
            "$a v $b", "$a vs $b", "$a at $b", "$a versus $b",
            "$b v $a", "$b vs $a", "$b at $a", "$b versus $a",
        )
        return patterns.any { hasWord(text, it) }
    }

    private fun matchesChannelNameEvent(
        normChannel: String,
        leagueTerms: List<String>,
        eventTerms: List<String>,
    ): Boolean {
        val hasDistinctLeague = leagueTerms.any { it.length >= 2 && it !in GENERIC_SPORT_WORDS && hasWord(normChannel, it) }
        val specificHits = eventTerms.count { hasWord(normChannel, it) }
        return (hasDistinctLeague && specificHits >= 1) ||
            specificHits >= 2 ||
            leagueTerms.any { it in DEDICATED_EVENT_CHANNEL_TERMS && hasWord(normChannel, it) }
    }

    private fun looksLikeSport(programme: IptvEpgEntry, normTitle: String, leagueTerms: List<String>): Boolean {
        if (NOT_LIVE_WORDS.any { hasWord(normTitle, it) }) return false
        if (leagueTerms.any { hasWord(normTitle, it) }) return true
        if (SPORT_TITLE_WORDS.any { hasWord(normTitle, it) }) return true
        return programme.kind?.let { normalize(it) } in SPORT_KINDS
    }

    private fun isClearlyNonSport(programme: IptvEpgEntry): Boolean {
        val kind = programme.kind?.let { normalize(it) } ?: return false
        return kind in NON_SPORT_KINDS
    }

    /** True when [normTitle] names an explicit `X at Y` / `X vs Y` matchup involving neither of our teams. */
    private fun isOtherGame(
        normTitle: String,
        awayTerms: List<String>,
        awayWeak: List<String>,
        homeTerms: List<String>,
        homeWeak: List<String>,
        abbrAway: String?,
        abbrHome: String?,
    ): Boolean {
        if (normTitle.isBlank() || (awayTerms.isEmpty() && homeTerms.isEmpty())) return false
        val padded = " $normTitle "
        if (!MATCHUP_REGEX.containsMatchIn(padded)) return false
        val mentionsUs = (awayTerms + awayWeak + homeTerms + homeWeak).any { hasWord(normTitle, it) } ||
            (abbrAway != null && hasWord(normTitle, abbrAway)) ||
            (abbrHome != null && hasWord(normTitle, abbrHome))
        return !mentionsUs
    }

    /** Rejects 24/7 loop channels, 6+ hour marathon blocks, replays, highlights and channel-name filler. */
    internal fun isFillerOrReplay(channel: IptvChannel, categoryName: String, programme: IptvEpgEntry): Boolean {
        if (programme.endMs - programme.startMs >= MAX_LIVE_PROGRAMME_MS) return true
        val normCat = normalize(categoryName)
        if (LOOP_WORDS.any { hasWord(normCat, it) || hasWord(normalize(channel.name), it) }) return true
        val normTitle = normalize(expandSymbols(programme.title))
        if (NOT_LIVE_WORDS.any { hasWord(normTitle, it) }) return true
        if (programme.kind?.let { normalize(it) } in setOf("sports non event", "classic sport event")) return true

        // Listing that merely repeats the channel's own multi-word name (unless it has "at"/"vs").
        if (!MATCHUP_REGEX.containsMatchIn(" $normTitle ")) {
            val cleanCh = stripChannelNoise(channel.name)
            val cleanTitle = stripChannelNoise(programme.title)
            if (cleanCh.contains(' ') && cleanCh.length >= 8 && cleanTitle == cleanCh) return true
        }
        return false
    }

    /** True for playlist separator rows like `"##### USA SPORTS #####"` or `"════ UK ════"`. */
    internal fun isBanner(name: String): Boolean {
        if (name.isBlank()) return false
        if (BANNER_RUN.containsMatchIn(name)) return true
        val alnum = name.count { it.isLetterOrDigit() }
        return name.length >= 6 && alnum * 10 < name.length * 4
    }

    /** Canonical lookup key for a broadcast network ("ESPN+" -> "espn plus", "FS1" -> "fs1"). */
    internal fun networkKey(name: String): String = normalize(expandSymbols(name))

    private fun networkPatterns(netKey: String): List<String> =
        NETWORKS[netKey] ?: run {
            val distinctive = netKey.split(' ').filter { it.length >= 3 && it !in GENERIC_NETWORK_WORDS }
            distinctive.ifEmpty { listOf(netKey).filter { it.length >= 2 } }
        }

    private fun matchesNetworkName(normChannel: String, netKey: String): Boolean {
        if (normChannel.isBlank() || netKey.isBlank()) return false
        val exclusions = NETWORK_NOT[netKey].orEmpty()
        if (exclusions.any { hasWord(normChannel, it) }) return false
        if (REPLAY_CHANNEL_WORDS.any { hasWord(normChannel, it) }) return false
        val mapped = NETWORKS[netKey]
        return if (mapped != null) {
            mapped.any { hasWord(normChannel, it) }
        } else {
            val required = networkPatterns(netKey)
            required.isNotEmpty() && required.all { hasWord(normChannel, it) }
        }
    }

    /** Strong names that identify a team on their own ("chicago bears", "bears", "man utd"). */
    private fun strongTeamTerms(team: Team): List<String> = buildList {
        val full = normalize(expandSymbols(team.name))
        val short = normalize(expandSymbols(team.shortName))
        val strippedFull = stripClubAffixes(full)
        val strippedShort = stripClubAffixes(short)
        add(full)
        add(short)
        if (strippedFull.length >= 3) add(strippedFull)
        if (strippedShort.length >= 3) add(strippedShort)
        initials(team.name)?.let(::add)
        spacedInitials(team.name)?.let(::add)
        CLUB_ALIASES[strippedFull]?.let(::addAll)
        CLUB_ALIASES[strippedShort]?.let(::addAll)
        // Safe 3-4 letter abbreviations ("ecu", "psg", "lafc") except common English words ("cat", "was").
        val abbr = normalize(team.abbreviation)
        if (abbr.length in 3..4 && abbr !in AMBIGUOUS_ABBR) add(abbr)
    }.filter { it.length >= 3 }.distinct()

    /** Secondary location/nickname tokens used when the opponent's primary name is already matched. */
    private fun weakTeamTerms(team: Team): List<String> = buildList {
        val words = stripClubAffixes(normalize(team.name)).split(' ').filter { it.length >= 4 }
        if (words.size >= 2) {
            add(words.first())
            add(words.last())
        }
    }.distinct()

    private fun stripClubAffixes(normName: String): String =
        normName.split(' ')
            .filter { it !in CLUB_AFFIXES }
            .joinToString(" ")
            .trim()

    private fun eventWords(event: SportsEvent, leagueTerms: List<String>): List<String> {
        val source = event.title ?: event.away.team.name
        val norm = normalize(expandSymbols(source))
        return norm.split(" vs ", " v ", " at ")
            .flatMap { part ->
                val trimmed = part.trim()
                listOf(trimmed) + trimmed.split(' ').filter { it.length >= 4 }
            }
            .map { it.trim() }
            .filter { it.length >= 4 && it !in GENERIC_EVENT_WORDS && it !in leagueTerms }
            .distinct()
    }

    /** "East Carolina" -> "ecarolina". */
    private fun initials(name: String): String? {
        val words = name.split(' ').filter { it.isNotBlank() }
        if (words.size < 2) return null
        return words.dropLast(1).joinToString("") { it.first().lowercaseChar().toString() } + normalize(words.last())
    }

    /** "East Carolina" -> "e carolina". */
    private fun spacedInitials(name: String): String? {
        val words = name.split(' ').filter { it.isNotBlank() }
        if (words.size != 2) return null
        return "${words.first().first().lowercaseChar()} ${normalize(words.last())}"
    }

    /** Region or language code of a channel ("UK", "US", "CA", "IE", "AU", or "lang:es"). */
    internal fun channelRegionCode(
        channelName: String,
        categoryName: String = "",
        categoryRegion: String? = null,
    ): String? {
        val lang = IptvLanguages.ofChannel(channelName, categoryName, categoryRegion)
        if (lang != "en" && lang != IptvLanguages.OTHER) return "lang:$lang"

        val iso = categoryRegion ?: IptvRegions.regionOf(channelName) ?: IptvRegions.regionOf(categoryName)
        return when (iso) {
            "GB" -> "UK"
            "US", "CA", "IE", "AU" -> iso
            "NZ" -> "AU"
            else -> null
        }
    }

    private fun stripChannelNoise(text: String): String =
        normalize(text.replace(CHANNEL_PREFIX, ""))
            .split(' ')
            .filter { it.isNotEmpty() && it !in NOISE_WORDS }
            .joinToString(" ")

    private fun expandSymbols(text: String): String =
        text.replace("+", " plus ").replace("@", " at ")

    internal fun hasWord(normalizedHaystack: String, normalizedNeedle: String): Boolean {
        if (normalizedHaystack.isEmpty() || normalizedNeedle.isEmpty()) return false
        return " $normalizedNeedle " in " $normalizedHaystack "
    }

    /**
     * Lowercased, accents removed, punctuation collapsed: keeps words separated by single spaces so
     * `"la liga"` never false-matches `"alaliga"`.
     */
    internal fun normalize(text: String): String {
        val plain = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFKD)
            .replace(Regex("""\p{Mn}+"""), "")
            .replace("&", " and ")
            .replace(Regex("""['\u2019\u02BC]"""), "")
        return plain.replace(Regex("""[^a-z0-9]+"""), " ").trim()
    }

    private val BANNER_RUN = Regex("""([#=*~_▬━─•■◆★|\-])\1{2,}""")
    private val MATCHUP_REGEX = Regex("""\s(?:at|vs|v|versus)\s""")
    private val CHANNEL_PREFIX = Regex("""^\s*[A-Za-z]{2,3}\s*[:|\-]\s*""")

    private val SPORT_KINDS = setOf(
        "sport", "sports", "football", "basketball", "hockey", "baseball", "soccer", "live sports",
        "tennis", "golf", "motor sport", "motorsport", "rugby", "cricket", "athletics", "martial sports",
    )
    private val NON_SPORT_KINDS = setOf("movie", "movies", "film", "films", "news", "kids", "children", "music", "shopping")
    private val SPORT_TITLE_WORDS = listOf(
        "football", "basketball", "hockey", "baseball", "soccer", "nfl", "nba", "nhl", "mlb", "mls",
        "ncaa", "college", "premier league", "champions league", "la liga", "serie a", "bundesliga",
        "ligue 1", "ufc", "formula 1", "nascar", "golf", "tennis", "rugby", "cricket", "live",
    )
    private val NOT_LIVE_WORDS = listOf(
        "replay", "replays", "classic", "classics", "highlights", "highlight", "encore",
        "documentary", "rewind", "archive", "vault", "best of",
    )
    private val REPLAY_CHANNEL_WORDS = listOf("replay", "replays", "24 7", "247", "classic", "classics", "vault", "encore")
    private val LOOP_WORDS = listOf("24 7", "247")
    private val NOISE_WORDS = setOf("fhd", "uhd", "hd", "sd", "4k", "hevc", "h265", "h264", "50fps", "60fps", "raw", "backup", "alt", "vip")
    private val GENERIC_SPORT_WORDS = setOf("football", "basketball", "hockey", "baseball", "soccer", "golf", "tennis", "cricket", "rugby")
    private val DEDICATED_EVENT_CHANNEL_TERMS = setOf("f1", "formula 1", "ufc", "nascar", "nfl", "nba", "nhl", "mlb")
    private val GENERIC_EVENT_WORDS = setOf(
        "grand", "prix", "championship", "championships", "open", "classic", "tour", "the", "final",
        "finals", "race", "series", "invitational", "masters", "trophy", "fight", "night", "main", "card",
    )
    private val GENERIC_NETWORK_WORDS = setOf("sports", "network", "tv", "channel", "the", "regional", "home", "plus")
    private val CLUB_AFFIXES = setOf("fc", "afc", "cf", "sc", "ac", "bc", "ssc", "rc", "cd", "ud")
    private val AMBIGUOUS_ABBR = setOf(
        "in", "no", "or", "la", "ne", "me", "us", "uk", "tv", "hd", "sd",
        "was", "can", "cat", "car", "sun", "net", "day", "all", "new", "one", "pro", "usa", "win",
    )

    private val CLUB_ALIASES: Map<String, List<String>> = mapOf(
        "manchester united" to listOf("man utd", "man united"),
        "man utd" to listOf("manchester united", "man united"),
        "manchester city" to listOf("man city"),
        "man city" to listOf("manchester city"),
        "tottenham hotspur" to listOf("tottenham", "spurs"),
        "tottenham" to listOf("tottenham hotspur", "spurs"),
        "wolverhampton wanderers" to listOf("wolves", "wolverhampton"),
        "wolves" to listOf("wolverhampton wanderers", "wolverhampton"),
        "newcastle united" to listOf("newcastle", "newcastle utd"),
        "west ham united" to listOf("west ham", "west ham utd"),
        "nottingham forest" to listOf("nottm forest", "forest"),
        "brighton and hove albion" to listOf("brighton"),
        "sheffield united" to listOf("sheff utd", "sheffield utd"),
        "paris saint germain" to listOf("psg", "paris sg"),
        "internazionale" to listOf("inter milan", "inter"),
        "inter milan" to listOf("internazionale"),
        "atletico madrid" to listOf("atletico"),
        "borussia dortmund" to listOf("dortmund", "bvb"),
        "bayern munich" to listOf("bayern munchen", "bayern"),
    )

    private val NETWORKS: Map<String, List<String>> = mapOf(
        "fox" to listOf("fox"),
        "cbs" to listOf("cbs"),
        "nbc" to listOf("nbc"),
        "abc" to listOf("abc"),
        "cw" to listOf("the cw", "cw"),
        "espn" to listOf("espn"),
        "espn2" to listOf("espn2", "espn 2"),
        "espnu" to listOf("espnu", "espn u"),
        "espnews" to listOf("espnews", "espn news"),
        "espn plus" to listOf("espn plus", "espn"),
        "espn deportes" to listOf("espn deportes"),
        "fs1" to listOf("fs1", "fox sports 1"),
        "fox sports 1" to listOf("fs1", "fox sports 1"),
        "fs2" to listOf("fs2", "fox sports 2"),
        "fox sports 2" to listOf("fs2", "fox sports 2"),
        "nfl net" to listOf("nfl network", "nfl net", "nfln"),
        "nfl network" to listOf("nfl network", "nfl net", "nfln"),
        "nfln" to listOf("nfl network", "nfl net", "nfln"),
        "nba tv" to listOf("nba tv", "nbatv"),
        "nhl net" to listOf("nhl network", "nhl net", "nhln"),
        "nhl network" to listOf("nhl network", "nhl net", "nhln"),
        "mlb net" to listOf("mlb network", "mlb net", "mlbn"),
        "mlb network" to listOf("mlb network", "mlb net", "mlbn"),
        "tnt" to listOf("tnt"),
        "tbs" to listOf("tbs"),
        "trutv" to listOf("trutv", "tru tv"),
        "usa net" to listOf("usa network", "usa net"),
        "usa network" to listOf("usa network", "usa net"),
        "cbssn" to listOf("cbs sports network", "cbssn", "cbs sn", "cbs sports net"),
        "cbs sn" to listOf("cbs sports network", "cbssn", "cbs sn", "cbs sports net"),
        "cbs sports network" to listOf("cbs sports network", "cbssn", "cbs sn", "cbs sports net"),
        "btn" to listOf("big ten network", "btn", "big ten"),
        "big ten network" to listOf("big ten network", "btn", "big ten"),
        "secn" to listOf("sec network", "secn"),
        "sec network" to listOf("sec network", "secn"),
        "accn" to listOf("acc network", "accn"),
        "acc network" to listOf("acc network", "accn"),
        "golf" to listOf("golf channel", "golf"),
        "golf channel" to listOf("golf channel", "golf"),
        "tennis channel" to listOf("tennis channel"),
        "peacock" to listOf("peacock"),
        "prime video" to listOf("prime video", "amazon prime"),
        "paramount plus" to listOf("paramount plus", "paramount"),
        "apple tv" to listOf("apple tv", "mls season pass"),
        "apple tv plus" to listOf("apple tv", "mls season pass"),
        "netflix" to listOf("netflix"),
        "bein sports" to listOf("bein sports", "bein"),
        "max" to listOf("max", "hbo max"),
        "sky sports" to listOf("sky sports"),
        "sky sports main event" to listOf("sky sports main event", "sky sports 1"),
        "sky sports football" to listOf("sky sports football"),
        "sky sports premier league" to listOf("sky sports premier league", "sky sports prem", "sky sports epl"),
        "sky sports f1" to listOf("sky sports f1", "sky sports formula 1"),
        "sky sports cricket" to listOf("sky sports cricket"),
        "sky sports golf" to listOf("sky sports golf"),
        "sky sports tennis" to listOf("sky sports tennis"),
        "tnt sports" to listOf("tnt sports", "tnt sport"),
        "tnt sports 1" to listOf("tnt sports 1", "tnt sport 1"),
        "tnt sports 2" to listOf("tnt sports 2", "tnt sport 2"),
        "tnt sports 3" to listOf("tnt sports 3", "tnt sport 3"),
        "tnt sports 4" to listOf("tnt sports 4", "tnt sport 4"),
        "tnt sport 1" to listOf("tnt sports 1", "tnt sport 1"),
        "tnt sport 2" to listOf("tnt sports 2", "tnt sport 2"),
        "premier sports" to listOf("premier sports", "premier sport"),
        "dazn" to listOf("dazn"),
        "itv" to listOf("itv1", "itv"),
        "itv1" to listOf("itv1", "itv"),
        "itv4" to listOf("itv4"),
        "bbc" to listOf("bbc one", "bbc two", "bbc 1", "bbc 2", "bbc"),
        "bbc one" to listOf("bbc one", "bbc 1"),
        "tsn" to listOf("tsn", "tsn1", "tsn2", "tsn3", "tsn4", "tsn5"),
        "sportsnet" to listOf("sportsnet"),
        "kayo" to listOf("kayo"),
        "stan sport" to listOf("stan sport"),
        "optus sport" to listOf("optus sport"),
        "tudn" to listOf("tudn"),
        "univision" to listOf("univision"),
        "telemundo" to listOf("telemundo"),
    )

    private val NETWORK_NOT: Map<String, List<String>> = mapOf(
        "fox" to listOf(
            "news", "business", "deportes", "sports 1", "sports 2", "fs1", "fs2", "soccer",
            "weather", "life", "kids", "movies", "classics", "comedy", "nation", "family", "crime", "reality",
        ),
        "cbs" to listOf("news", "sports network", "cbssn", "golazo", "reality", "drama", "action"),
        "nbc" to listOf("news", "nbcsn", "universo", "golf"),
        "abc" to listOf("news", "family", "kids", "australia"),
        "espn" to listOf("2", "u", "news", "deportes", "classic", "espn2", "espnu", "espnews"),
        "espn plus" to listOf("news", "deportes", "classic", "espnews"),
        "tnt" to listOf("sports 1", "sports 2", "sports 3", "sports 4", "sport 1", "sport 2", "drama", "series", "film", "comedy"),
        "tbs" to listOf("news"),
        "max" to listOf("cinemax", "maximum", "maxx", "movies"),
        "itv" to listOf("itv2", "itv3", "itv4", "itvbe"),
        "bbc" to listOf("news", "parliament", "alba", "four", "cbeebies", "cbbc"),
    )
}

/**
 * Region & language choices for filtering a game's matched IPTV channels on the game page,
 * modelled after NuvioPlusIPTV's `ChannelRegions`.
 */
internal object SportsChannelRegions {
    const val ENGLISH = "EN"
    private val ENGLISH_REGIONS = setOf("UK", "US", "IE", "CA", "AU")

    val all: List<Pair<String, String>> = listOf(
        "" to "All regions",
        ENGLISH to "English (US, UK, Canada, Ireland, Australia)",
        "US" to "United States",
        "UK" to "United Kingdom",
        "CA" to "Canada",
        "IE" to "Ireland",
        "AU" to "Australia & New Zealand",
        "lang:es" to "Spanish",
        "lang:fr" to "French",
        "lang:de" to "German",
        "lang:it" to "Italian",
        "lang:pt" to "Portuguese",
        "lang:nl" to "Dutch",
        "lang:pl" to "Polish",
        "lang:tr" to "Turkish",
        "lang:ar" to "Arabic",
    )

    fun name(code: String): String = all.firstOrNull { it.first == code }?.second
        ?: if (code.startsWith("lang:")) IptvLanguages.label(code.removePrefix("lang:")) else code

    fun shortName(code: String): String = when (code) {
        "" -> "All regions"
        ENGLISH -> "English"
        "UK" -> "UK"
        "US" -> "USA"
        "CA" -> "Canada"
        "IE" -> "Ireland"
        "AU" -> "Australia"
        else -> name(code)
    }

    fun includes(channelCode: String?, choice: String): Boolean = when {
        choice.isBlank() -> true
        channelCode == null -> false
        choice == ENGLISH -> channelCode in ENGLISH_REGIONS
        else -> channelCode == choice
    }

    /** Region choices actually present among a game's matched channels, with counts. */
    fun choicesFor(codes: Collection<String?>, current: String): List<Pair<String, Int>> {
        val counts = codes.filterNotNull().groupingBy { it }.eachCount()
        val out = ArrayList<Pair<String, Int>>()
        val englishCount = codes.count { it != null && it in ENGLISH_REGIONS }
        val englishRegions = counts.keys.count { it in ENGLISH_REGIONS }
        if (englishCount > 0 && (englishRegions > 1 || counts.keys.any { it !in ENGLISH_REGIONS })) {
            out += ENGLISH to englishCount
        }
        counts.entries.sortedByDescending { it.value }.forEach { (code, n) -> out += code to n }
        if (current.isNotBlank() && out.none { it.first == current }) {
            out += current to 0
        }
        return out
    }
}
