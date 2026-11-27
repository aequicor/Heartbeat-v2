package io.github.aequicor.heartbeat.features.aiengine.api.session

import kotlinx.coroutines.flow.StateFlow

public interface Session {
    public val key: SessionKey

    public val supportsEmbeddedResources: Boolean get() = false

    /** The current status and message window. A newly opened session shows its latest partition. */
    public val state: StateFlow<SessionState>

    /** Applies an advertised value and returns all selectors, including dependent choices. */
    @Throws(Exception::class)
    public suspend fun selectSetting(id: String, value: String): SessionSettings =
        throw UnsupportedOperationException("This session has no selectable settings")

    public fun dispatch(event: SessionEvent)
}
