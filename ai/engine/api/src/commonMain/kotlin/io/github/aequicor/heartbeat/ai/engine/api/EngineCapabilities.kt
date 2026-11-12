package io.github.aequicor.heartbeat.ai.engine.api

/** Optional engine operations discovered from the agent handshake, never assumed from its type. */
public enum class EngineCapability {
    SESSION_LIST,
    SESSION_RESUME,
    SESSION_CLOSE,
}

/** Capabilities advertised by the agent after a successful connect. */
public data class EngineCapabilities(
    val supported: Set<EngineCapability> = setOf(),
) {
    public fun supports(capability: EngineCapability): Boolean = capability in supported
}
