package io.github.aequicor.heartbeat.features.aienginefacade.di

import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineConnection
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineKey

internal data class TestEngineKey(override val id: String, override val name: String = id) : AiEngineKey {
    override val description: String = name
    override val connection: AiEngineConnection = AiEngineConnection.EMBEDDED
}
