package io.github.aequicor.heartbeat.features.aiengine.api.session

import kotlinx.coroutines.flow.StateFlow

public interface Session {
    public val key: SessionKey

    /** The current status and message window. A newly opened session shows its latest partition. */
    public val state: StateFlow<SessionState>

    public fun dispatch(event: SessionEvent)
}
