package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatSecurityMode
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.SecurityMode

internal fun ChatSecurityMode.display() = SecurityMode(
    options = options.map { SecurityMode.Option(it.id, it.name.ifBlank { it.id }, it.description, it.groupName ?: it.group) },
    selected = selectedValue,
    requiresUserChoice = requiresUserChoice,
)
