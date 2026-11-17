package com.nuvio.app.core.build

enum class TrailerPlaybackMode {
    IN_APP,
    EXTERNAL,
}

expect object AppFeaturePolicy {
    val pluginsEnabled: Boolean
    val downloadsEnabled: Boolean
    val notificationsEnabled: Boolean
    val p2pEnabled: Boolean
    val trailerPlaybackMode: TrailerPlaybackMode
    val heroTrailerPlaybackSupported: Boolean
    val inAppUpdaterEnabled: Boolean
    val imdbRatingLogoEnabled: Boolean

    /** Whether the user may point this build at a self-hosted Nuvio backend. */
    val customServerConnectionsEnabled: Boolean

    /**
     * Whether this build carries the Sports tab (see fork:sports). Off on every build that is not
     * launched with `-Pnuvio.sports=true`, so an ordinary build behaves exactly as upstream does:
     * the Sports entry, its settings page and its store are all absent, not merely hidden.
     */
    val sportsEnabled: Boolean
}
