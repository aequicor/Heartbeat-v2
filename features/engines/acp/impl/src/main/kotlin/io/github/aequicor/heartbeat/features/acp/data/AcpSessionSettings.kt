package io.github.aequicor.heartbeat.features.acp.data

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.model.AcpCreatedSessionResponse
import com.agentclientprotocol.model.AcpMethod.AgentMethods.V1
import com.agentclientprotocol.model.ModelId
import com.agentclientprotocol.model.SessionConfigId
import com.agentclientprotocol.model.SessionConfigOption
import com.agentclientprotocol.model.SessionConfigOptionCategory
import com.agentclientprotocol.model.SessionConfigOptionValue
import com.agentclientprotocol.model.SessionConfigSelectOptions
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.model.SessionModelState
import com.agentclientprotocol.model.SetSessionConfigOptionRequest
import com.agentclientprotocol.model.SetSessionModelRequest
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.protocol.invoke
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionSettings

@OptIn(UnstableApi::class)
internal class AcpSessionSettings(
    private val sessionId: SessionId,
    private val protocol: Protocol,
    private val lock: Any,
    private val publish: () -> Unit,
) {

    private var options: List<SessionConfigOption>? = null
    private var legacy: SessionModelState? = null
    private val unconfirmed = mutableSetOf<String>()
    var current = SessionSettings()
        private set

    fun initialize(response: AcpCreatedSessionResponse) = synchronized(lock) {
        if (options == null) options = response.configOptions
        legacy = response.models
        refresh()
    }

    fun update(values: List<SessionConfigOption>) = synchronized(lock) {
        options = values
        values.forEach { unconfirmed.remove(it.id.value) }
        refresh()
    }

    suspend fun select(id: String, value: String, checkAvailable: () -> Unit): SessionSettings {
        val legacyModel = synchronized(lock) {
            checkAvailable()
            val selector = listOfNotNull(current.model, current.reasoning, current.securityMode).first { it.id == id }
            require(selector.choices.any { it.id == value })
            if (selector.current == value && id !in unconfirmed) return current
            id == LegacyModelSelector && options.orEmpty().none { it.id.value == id }
        }
        try {
            withinRequestTimeout {
                if (legacyModel) {
                    V1.SessionSetModel(protocol, SetSessionModelRequest(sessionId, ModelId(value)))
                    synchronized(lock) {
                        legacy = legacy?.copy(currentModelId = ModelId(value))
                        unconfirmed.remove(id)
                        refresh()
                    }
                } else {
                    val request = SetSessionConfigOptionRequest(
                        sessionId, SessionConfigId(id), SessionConfigOptionValue.StringValue(value),
                    )
                    update(V1.SessionSetConfigOption(protocol, request).configOptions)
                }
            }
        } catch (failure: Exception) {
            // A failed request can leave the cached value behind the agent's setting.
            synchronized(lock) {
                unconfirmed.add(id)
                refresh()
            }
            throw failure
        }
        return synchronized(lock) {
            checkAvailable()
            current
        }
    }

    private fun refresh() {
        val selects = options.orEmpty().filterIsInstance<SessionConfigOption.Select>()
        val model = selects.firstOrNull { it.category == SessionConfigOptionCategory.MODEL }
        val reasoning = selects.firstOrNull { it.category == SessionConfigOptionCategory.THOUGHT_LEVEL }
        val legacyModel = legacy?.let { state ->
            SessionSettings.Selector(
                LegacyModelSelector, state.currentModelId.value,
                state.availableModels.map { SessionSettings.Choice(it.modelId.value, it.name) },
            )
        }
        val securityMode = selects.firstOrNull { it.category == SessionConfigOptionCategory.MODE }
        current = SessionSettings(
            model?.toSelector() ?: legacyModel,
            reasoning?.toSelector(),
            securityMode?.toSelector(currentConfirmed = securityMode.id.value !in unconfirmed),
        )
        publish()
    }

    private companion object {

        const val LegacyModelSelector = "legacy:model"
    }
}

private fun SessionConfigOption.Select.toSelector(currentConfirmed: Boolean = true): SessionSettings.Selector {
    val choices = when (val list = options) {
        is SessionConfigSelectOptions.Flat -> list.options.map {
            SessionSettings.Choice(it.value.value, it.name.ifBlank { it.value.value }, it.description)
        }
        is SessionConfigSelectOptions.Grouped -> list.groups.flatMap { group ->
            group.options.map {
                SessionSettings.Choice(it.value.value, it.name.ifBlank { it.value.value }, it.description, group.group.value, group.name)
            }
        }
    }
    return SessionSettings.Selector(id.value, currentValue.value, choices, currentConfirmed)
}
