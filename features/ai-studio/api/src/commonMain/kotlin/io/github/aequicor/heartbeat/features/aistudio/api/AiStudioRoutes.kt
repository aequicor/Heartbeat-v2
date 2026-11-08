package io.github.aequicor.heartbeat.features.aistudio.api

import io.github.aequicor.heartbeat.navigation.api.AppRoute

/** Routes of the AI studio workspace. */
public object AiStudioRoutes {

    /** Stable feature identifier used by the shell registry. */
    public const val FEATURE_ID: String = "ai-studio"

    /** Main workspace route: the application starts here. */
    public fun main(): AppRoute = AppRoute(featureId = FEATURE_ID)
}
