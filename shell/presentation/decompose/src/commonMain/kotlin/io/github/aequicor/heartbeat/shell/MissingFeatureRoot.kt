package io.github.aequicor.heartbeat.shell

import io.github.aequicor.heartbeat.navigation.decompose.FeatureRoot

/**
 * Root of a route whose feature is not registered, for example when a saved route survives the
 * removal of a feature. Production keeps the navigation alive and shows a fallback instead of
 * crashing; tests treat the missing renderer as a configuration error.
 */
internal data class MissingFeatureRoot(
    val featureId: String,
) : FeatureRoot
