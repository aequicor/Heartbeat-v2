package io.github.aequicor.heartbeat.features.aiengine.api

/** Opaque tokens for older and newer partitions. A null token marks the end in that direction. */
public data class AiEngineMessageWindowCursor(
    val previous: String? = null,
    val next: String? = null,
)
