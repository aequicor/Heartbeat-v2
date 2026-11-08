package io.github.aequicor.heartbeat.shell

import io.github.aequicor.heartbeat.navigation.decompose.DecomposeFeatureEntry

/**
 * Feature entry points by `featureId`. The shell never enumerates feature screens: it resolves the
 * entry that a route names, so a new feature changes only the composition root.
 */
class FeatureRegistry(
    entries: Set<DecomposeFeatureEntry>,
) {

    private val byId: Map<String, DecomposeFeatureEntry> = entries.associateBy { it.featureId }

    init {
        require(byId.size == entries.size) {
            "Duplicate feature registrations: " +
                entries.groupBy { it.featureId }.filterValues { it.size > 1 }.keys
        }
    }

    fun find(featureId: String): DecomposeFeatureEntry? = byId[featureId]

    fun resolve(featureId: String): DecomposeFeatureEntry =
        requireNotNull(find(featureId)) {
            "Feature not registered: $featureId"
        }
}
