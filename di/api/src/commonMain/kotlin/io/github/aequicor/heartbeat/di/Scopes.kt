package io.github.aequicor.heartbeat.di

import dev.zacsweers.metro.Scope

/** Lifetime of the application instance. */
@Scope
annotation class AppScope

/** Lifetime of the current user session. */
@Scope
annotation class SessionScope
