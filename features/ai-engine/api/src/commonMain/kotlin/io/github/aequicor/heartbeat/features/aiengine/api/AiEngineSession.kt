package io.github.aequicor.heartbeat.features.aiengine.api

import kotlinx.coroutines.flow.StateFlow

public interface AiEngineSession {
    public val key: AiEngineSessionKey

    /** The current status and message window. A newly opened session shows its latest partition. */
    public val state: StateFlow<AiEngineSessionState>

    public fun dispatch(event: AiEngineSessionEvent)
}
