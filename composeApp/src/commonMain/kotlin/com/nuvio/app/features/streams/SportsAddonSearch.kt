package com.nuvio.app.features.streams

import com.nuvio.app.features.addons.AddonCatalog
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.home.MetaPreview

/** A sports-only fallback through searchable catalogs, for addons whose stream resource is movie/tv. */
internal data class SportsAddonCatalogTarget(
    val addon: ManagedAddon,
    val type: String,
    val catalog: AddonCatalog,
) {
    val groupId: String
        get() = "${addon.streamAddonInstanceId(addon.manifest?.id.orEmpty())}:sports-search:$type:${catalog.id}"
}

internal fun sportsAddonCatalogTargets(addons: List<ManagedAddon>): List<SportsAddonCatalogTarget> =
    addons.asSequence()
        .filter { it.enabled }
        .mapNotNull { addon -> addon.manifest?.let { addon to it } }
        .flatMap { (addon, manifest) ->
            manifest.catalogs.asSequence()
                .filter { catalog -> catalog.supportsSportsSearch() }
                .filter { catalog ->
                    manifest.resources.any { resource ->
                        resource.name.equals("stream", ignoreCase = true) &&
                            resource.types.any { it.equals(catalog.type, ignoreCase = true) }
                    }
                }
                .map { catalog -> SportsAddonCatalogTarget(addon, catalog.type, catalog) }
        }
        .toList()

/** A title such as "Akron @ UMass" becomes two focused catalog searches. */
internal fun sportsSearchTerms(eventTitle: String): List<String> =
    eventTitle.split(Regex("\\s+(?:@|vs\\.?|v\\.?|at)\\s+", RegexOption.IGNORE_CASE))
        .map(String::trim)
        .filter { it.length >= 2 }
        .distinctBy { it.lowercase() }
        .take(2)

internal fun sportsMatchesEvent(meta: MetaPreview, searchTerms: List<String>): Boolean {
    if (searchTerms.size < 2) return false
    val name = meta.name.lowercase()
    return searchTerms.all { term ->
        val normalized = term.lowercase()
        name.contains(normalized) ||
            (normalized == "umass" && name.contains("massachusetts")) ||
            (normalized == "massachusetts" && name.contains("umass"))
    }
}

private fun AddonCatalog.supportsSportsSearch(): Boolean =
    extra.any { it.name.equals("search", ignoreCase = true) } &&
        extra.none { it.isRequired && !it.name.equals("search", ignoreCase = true) }
