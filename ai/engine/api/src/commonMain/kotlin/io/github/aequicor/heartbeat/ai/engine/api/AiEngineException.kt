package io.github.aequicor.heartbeat.ai.engine.api

/**
 * Failure of an engine operation that cannot be expressed as a state.
 *
 * State transitions are published through [AiEngine.state] and [AiSession.state]; exceptions
 * describe the result of a suspend call: a missing capability, a call before the connection is
 * ready, a protocol error or a busy session.
 */
public sealed class AiEngineException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** The agent did not advertise [capability]; the request was not sent. */
    public class Unsupported(
        public val capability: EngineCapability,
    ) : AiEngineException("Engine does not support ${capability.name}")

    /** The engine is not ready for the operation. */
    public class NotReady(
        public val state: EngineState,
    ) : AiEngineException("Engine is not ready: $state")

    /** The agent rejected the request with a protocol error. */
    public class Protocol(
        public val code: Int,
        message: String,
        cause: Throwable? = null,
    ) : AiEngineException("Agent protocol error $code: $message", cause)

    /** The session is already running a turn. */
    public class SessionBusy(
        public val session: SessionKey,
    ) : AiEngineException("Session is busy: ${session.id}")

    /** The session is unknown to the engine. */
    public class SessionNotFound(
        public val session: SessionKey,
    ) : AiEngineException("Session is not open: ${session.id}")
}
