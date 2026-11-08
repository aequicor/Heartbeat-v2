package io.github.aequicor.heartbeat.di

/**
 * Application-wide scope key.
 *
 * Used both as the aggregation scope of the platform graph (`@DependencyGraph(AppScope::class)`,
 * `@ContributesIntoSet(AppScope::class)`, `@ContributesBinding(AppScope::class)`) and as the
 * lifetime key of app-wide singletons (`@SingleIn(AppScope::class)`).
 */
@Suppress("AbstractClassCanBeInterface")
public abstract class AppScope private constructor()

/** Key of the current user session; reserved for future session-scoped graphs. */
@Suppress("AbstractClassCanBeInterface")
public abstract class SessionScope private constructor()
