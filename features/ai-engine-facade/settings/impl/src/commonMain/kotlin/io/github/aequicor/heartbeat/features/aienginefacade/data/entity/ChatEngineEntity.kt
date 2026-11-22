package io.github.aequicor.heartbeat.features.aienginefacade.data.entity

import io.github.aequicor.heartbeat.features.aienginefacade.db.Chat_engine
import io.github.aequicor.heartbeat.features.aienginefacade.domain.model.ChatEngine
import kotlinx.serialization.json.Json

internal fun Chat_engine.toDomain(): ChatEngine =
    ChatEngine(id, name, connection_id, Json.decodeFromString(local_settings_json), chat_rules)

internal fun ChatEngine.toEntity(): Chat_engine =
    Chat_engine(id, name, connectionId, Json.encodeToString(localSettings), rules)
