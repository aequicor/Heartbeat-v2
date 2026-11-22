package io.github.aequicor.heartbeat.features.aienginefacade.domain.model

internal data class ChatEngine(
    val id: String,
    val name: String,
    val connectionId: String,
    val localSettings: Map<String, String> = mapOf(),
    val rules: String = "",
)
