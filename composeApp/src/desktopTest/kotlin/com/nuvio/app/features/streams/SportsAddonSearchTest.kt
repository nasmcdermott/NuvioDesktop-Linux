package com.nuvio.app.features.streams

import com.nuvio.app.features.addons.AddonCatalog
import com.nuvio.app.features.addons.AddonManifest
import com.nuvio.app.features.addons.AddonResource
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.home.MetaPreview
import kotlin.test.Test
import kotlin.test.assertEquals

class SportsAddonSearchTest {
    @Test
    fun `sports lookup includes ordinary searchable movie and series catalogs with streams`() {
        val manifest = manifest(
            resources = listOf(AddonResource("stream", listOf("movie", "series"))),
            catalogs = listOf(
                AddonCatalog("movie", "sports-search-movies", "Sports Movies", extra = listOf(com.nuvio.app.features.addons.AddonExtraProperty("search"))),
                AddonCatalog("series", "sports-search-series", "Sports Series", extra = listOf(com.nuvio.app.features.addons.AddonExtraProperty("search"))),
                AddonCatalog("movie", "sports-no-search", "Sports without search"),
                AddonCatalog("movie", "search-movies", "Movies", extra = listOf(com.nuvio.app.features.addons.AddonExtraProperty("search"))),
                AddonCatalog("series", "search-series", "Series", extra = listOf(com.nuvio.app.features.addons.AddonExtraProperty("search"))),
                AddonCatalog("channel", "search-channels", "Channels", extra = listOf(com.nuvio.app.features.addons.AddonExtraProperty("search"))),
            ),
        )

        val targets = sportsAddonCatalogTargets(
            listOf(
                ManagedAddon(manifest.transportUrl, manifest = manifest),
                ManagedAddon("https://example.test/disabled.json", manifest = manifest, enabled = false),
            ),
        )
        assertEquals(
            listOf("sports-search-movies", "sports-search-series", "search-movies", "search-series"),
            targets.map { it.catalog.id },
        )
    }

    @Test
    fun `sports lookup matches full matchup and either team's search result`() {
        val terms = sportsSearchTerms("Akron @ UMass")
        val metas = listOf(
            MetaPreview(id = "1", type = "movie", name = "Akron Zips vs Massachusetts Minutemen"),
            MetaPreview(id = "2", type = "series", name = "UMass Minutemen live"),
            MetaPreview(id = "3", type = "movie", name = "Boston Celtics vs Miami Heat"),
        )

        assertEquals(listOf("Akron", "UMass"), terms)
        assertEquals(listOf("1"), metas.filter { sportsMatchesEvent(it, terms) }.map { it.id })
    }

    private fun manifest(resources: List<AddonResource>, catalogs: List<AddonCatalog>) = AddonManifest(
        id = "sports-search",
        name = "Sports Search",
        description = "",
        version = "1",
        resources = resources,
        types = listOf("movie", "series"),
        catalogs = catalogs,
        transportUrl = "https://example.test/manifest.json",
    )
}
