package io.github.aequicor.heartbeat.features.aiengine.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

public interface AiEngineSession {
    public val key: AiEngineSessionKey
    public val state: StateFlow<AiEngineSessionState>

    /** Session updates delivered independently of the facade's state aggregation. */
    public val updates: Flow<AiEngineSessionUpdate>

    public fun dispatch(event: AiEngineSessionEvent)
}
