package io.github.aequicor.heartbeat.features.aienginefacade.data.entity

import io.github.aequicor.heartbeat.features.aienginefacade.db.Acp_connection
import io.github.aequicor.heartbeat.features.aienginefacade.domain.model.AcpConnectionSettings
import kotlinx.serialization.json.Json

internal fun Acp_connection.toDomain(): AcpConnectionSettings =
    AcpConnectionSettings(
        id, name, preset, command, argument_text, argument_separator.single(), Json.decodeFromString(environment_json),
    )

internal fun AcpConnectionSettings.toEntity(): Acp_connection =
    Acp_connection(id, name, preset, command, argumentText, separator.toString(), Json.encodeToString(environment))
