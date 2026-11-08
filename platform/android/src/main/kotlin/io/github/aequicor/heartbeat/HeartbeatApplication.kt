package io.github.aequicor.heartbeat

import android.app.Application
import dev.zacsweers.metro.createGraph
import io.github.aequicor.heartbeat.di.AndroidGraph

/**
 * Process-wide composition root.
 *
 * The Metro graph is created once and outlives Activity recreation, so `@SingleIn(AppScope::class)`
 * bindings keep their state across configuration changes instead of being reset on every `onCreate`.
 */
class HeartbeatApplication : Application() {

    val graph: AndroidGraph by lazy { createGraph<AndroidGraph>() }
}
