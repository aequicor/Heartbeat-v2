package io.github.aequicor.heartbeat.features.aistudio.component

import io.github.aequicor.heartbeat.navigation.decompose.FeatureRoot

/** Decompose root of the AI studio workspace. Screens stay private to the feature. */
interface AiStudioComponent : FeatureRoot {

    /** Opens the settings shell. */
    fun openSettings()
}
