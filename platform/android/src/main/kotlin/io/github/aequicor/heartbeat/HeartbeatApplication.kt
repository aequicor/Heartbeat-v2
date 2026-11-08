package io.github.aequicor.heartbeat

import android.app.Application
import dev.zacsweers.metro.createGraphFactory
import io.github.aequicor.heartbeat.di.AndroidGraph

/**
 * Process-wide composition root.
 *
 * The Metro graph is created once and outlives Activity recreation, so `@SingleIn(AppScope::class)`
 * bindings keep their state across configuration changes instead of being reset on every `onCreate`.
 * The database is opened during `Application.onCreate`, before any Activity can request a
 * repository; a migration failure fails the process instead of silently recreating user data.
 */
class HeartbeatApplication : Application() {

    val graph: AndroidGraph by lazy {
        createGraphFactory<AndroidGraph.Factory>().create(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        graph.databaseInitializer.initialize()
    }
}
