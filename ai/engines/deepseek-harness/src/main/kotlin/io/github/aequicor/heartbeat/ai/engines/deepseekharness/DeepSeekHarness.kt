package io.github.aequicor.heartbeat.ai.engines.deepseekharness

import io.github.aequicor.heartbeat.ai.engine.api.EngineConnection
import io.github.aequicor.heartbeat.ai.engine.api.EngineKey

/** Catalog entry of the DeepSeek Harness agent. Identity is the id; metadata is display-only. */
public object DeepSeekHarness {

    public val key: EngineKey = EngineKey(
        id = "acp.deepseek-harness",
        name = "DeepSeek Harness",
        description = "DeepSeek coding agent over ACP",
        connection = EngineConnection.ACP,
    )
}
