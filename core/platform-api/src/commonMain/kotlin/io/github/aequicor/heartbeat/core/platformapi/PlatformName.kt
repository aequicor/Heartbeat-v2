package io.github.aequicor.heartbeat.core.platformapi

/**
 * Platform name of the current runtime: Android, JVM, iOS, macOS, Windows or
 * Linux. Each target provides the actual implementation.
 */
expect fun currentPlatformName(): String
