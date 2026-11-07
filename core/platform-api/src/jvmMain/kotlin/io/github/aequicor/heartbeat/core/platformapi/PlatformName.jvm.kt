package io.github.aequicor.heartbeat.core.platformapi

actual fun currentPlatformName(): String = System.getProperty("os.name").orEmpty()
