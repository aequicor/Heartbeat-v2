package io.github.aequicor.heartbeat.features.aistudio.domain.model

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ChatEngineOptions.Choice

internal data class ChatDraftState(
    val engines: List<Choice> = emptyList(),
    val confirmed: Selection? = null,
    val editing: Selection? = null,
    val options: ChatEngineOptions? = null,
    val securityMode: ChatSecurityMode? = null,
    val securityModeError: String? = null,
    val busy: Boolean = false,
    val failed: Boolean = false,
) {

    data class Selection(val engine: Choice, val model: Choice? = null, val level: Choice? = null) {

        fun configuration() = ChatConfiguration(engine.id, model?.id, level?.id)
    }

    val canConfirm: Boolean get() = !busy && !failed && selectionComplete

    val selectionComplete: Boolean get() {
        val choice = editing ?: return false
        val available = options ?: return false
        return available.model.accepts(choice.model) && available.reasoning.accepts(choice.level)
    }

    private fun ChatEngineOptions.Selector?.accepts(value: Choice?): Boolean =
        if (this == null) value == null else choices.any { it.id == value?.id }
}
