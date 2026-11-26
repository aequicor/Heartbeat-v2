package io.github.aequicor.heartbeat.features.aistudio.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal data class ChatEngineOptions(
    val model: Selector?,
    val reasoning: Selector?,
    val securityMode: Selector? = null,
) {

    @Serializable
    data class Choice(
        @SerialName("value") val id: String,
        val name: String = id,
        val description: String? = null,
        val group: String? = null,
        val groupName: String? = null,
    )

    data class Selector(
        val id: String,
        val current: String,
        val choices: List<Choice>,
        val currentConfirmed: Boolean = true,
    )
}
