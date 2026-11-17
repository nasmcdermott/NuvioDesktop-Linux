package com.nuvio.app.features.sports

import java.nio.file.Path

/**
 * Where [SportsCache] keeps its files: `sports-cache/` in the active account folder.
 *
 * An expect/actual only because the desktop storage directory is `internal` to desktopMain; the
 * cache itself is plain JVM code. Same split as [com.nuvio.app.features.iptv.IptvCacheStorage], and
 * for the same reason it sits beside the IPTV channel cache rather than in the settings store: what
 * Settings > Backup carries is `*.properties`, so fetched games stay out of a backup by not being
 * one.
 */
internal expect object SportsCacheFiles {
    fun dir(): Path
}
