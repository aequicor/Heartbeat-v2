package io.github.aequicor.heartbeat.features.aiengine.api.session

/** The operation reported by the engine, independent of its title or output. */
public enum class ToolCallKind {
    READ,
    EDIT,
    DELETE,
    MOVE,
    SEARCH,
    EXECUTE,
    THINK,
    FETCH,
    SWITCH_MODE,
    OTHER,
}
