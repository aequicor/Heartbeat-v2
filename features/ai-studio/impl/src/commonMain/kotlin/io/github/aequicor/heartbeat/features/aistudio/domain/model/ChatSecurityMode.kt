package io.github.aequicor.heartbeat.features.aistudio.domain.model

import kotlinx.serialization.Serializable

@Serializable
internal data class ChatSecurityMode(
    val configId: String,
    val options: List<ChatEngineOptions.Choice>,
    val confirmedValue: String? = null,
    val pendingValue: String? = null,
    val requiresUserChoice: Boolean = false,
) {

    val selectedValue get() = pendingValue ?: confirmedValue

    fun choose(value: String): ChatSecurityMode {
        require(options.any { it.id == value }) { "Список режимов изменился" }
        return copy(pendingValue = value, requiresUserChoice = false)
    }

    @Serializable
    data class Preference(val configId: String, val options: List<ChatEngineOptions.Choice>, val preferredValue: String) {

        fun forNewChat() = ChatSecurityMode(configId, options, pendingValue = preferredValue)
    }

    companion object {

        fun reconcile(previous: ChatSecurityMode?, selector: ChatEngineOptions.Selector?): ChatSecurityMode? {
            if (selector == null || selector.choices.isEmpty()) {
                return previous?.copy(options = emptyList(), requiresUserChoice = true)
            }
            val sameSelector = previous?.configId == selector.id
            val pending = previous?.pendingValue
            val available = selector.choices.any { it.id == selector.current }
            val known = previous == null || sameSelector && previous.options.any { it.id == selector.current }
            val next = ChatSecurityMode(selector.id, selector.choices, previous?.confirmedValue)
            if (pending != null && sameSelector && selector.choices.any { it.id == pending }) {
                return if (selector.currentConfirmed && pending == selector.current) next.copy(confirmedValue = pending)
                else next.copy(pendingValue = pending)
            }
            return if (pending != null || previous?.requiresUserChoice == true || !selector.currentConfirmed || !available || !known) {
                next.copy(requiresUserChoice = true)
            } else next.copy(confirmedValue = selector.current)
        }
    }
}
