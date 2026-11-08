package io.github.aequicor.heartbeat.features.settings.api

import io.github.aequicor.heartbeat.navigation.api.AppRoute

/** Routes of the settings feature. */
public object SettingsRoutes {

    /** Stable feature identifier used by the shell registry. */
    public const val FEATURE_ID: String = "settings"

    /** Opens the settings shell with the default page selected. */
    public fun main(): AppRoute = AppRoute(featureId = FEATURE_ID)

    /** Opens the settings shell with [pageId] selected directly. */
    public fun page(pageId: String): AppRoute = AppRoute(featureId = FEATURE_ID, destination = pageId)
}
