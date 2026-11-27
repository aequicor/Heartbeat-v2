package io.github.aequicor.heartbeat.features.acp.data

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.rpc.ACPJson
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionSubagents.Activity
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionSubagents.Subagent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Owned by the root session and accessed under its lock. */
@OptIn(UnstableApi::class)
internal class AcpSubagents(private val dshAgent: Boolean) {

    private class Child(val parentId: String, val timeline: AcpTimeline) {

        var title: String? = null
        var description: String? = null
        var activity = Activity.Unknown
    }

    private val children = linkedMapOf<String, Child>()
    private val snapshots = MutableStateFlow<List<Subagent>>(emptyList())
    val state = snapshots.asStateFlow()

    fun announce(parentId: String, id: String, update: JsonObject, replay: Boolean) {
        val child = children.getOrPut(id) { Child(parentId, AcpTimeline(dshAgent)) }
        if (child.parentId != parentId) return
        if ("title" in update) child.title = update.string("title")
        if ("description" in update) child.description = update.string("description")
        if (!replay && "state" in update) {
            val activity = when ((update["state"] as? JsonObject)?.string("state")) {
                "running" -> Activity.Running
                "requires_action" -> Activity.RequiresAction
                "idle" -> Activity.Idle
                else -> Activity.Unknown
            }
            if (activity == Activity.Idle) child.timeline.breakMessage()
            child.activity = activity
        }
        publish()
    }

    fun update(id: String, update: SessionUpdate) {
        val child = children[id] ?: return
        val timeline = child.timeline
        when (update) {
            is SessionUpdate.UserMessageChunk -> timeline.userChunk(update)
            is SessionUpdate.AgentMessageChunk -> timeline.agentChunk(update.content, update.messageId?.value)
            is SessionUpdate.AgentThoughtChunk -> timeline.agentChunk(update.content, update.messageId?.value, reasoning = true)
            is SessionUpdate.ToolCall -> timeline.toolCall(update)
            is SessionUpdate.ToolCallUpdate -> timeline.toolCallUpdate(update)
            is SessionUpdate.PlanUpdate -> timeline.plan(update.entries.map { it.content })
            is SessionUpdate.UnknownSessionUpdate -> directedMessage(timeline, id, update)
            else -> return
        }
        publish()
    }

    private fun directedMessage(timeline: AcpTimeline, id: String, update: SessionUpdate.UnknownSessionUpdate) {
        val payload = update.rawJson
        val messageId = payload.string("messageId") ?: return
        when (update.sessionUpdateType) {
            "session_message" -> {
                val content = payload["content"] as? JsonArray ?: return
                timeline.directedMessage(
                    messageId, content.map { ACPJson.decodeFromJsonElement(ContentBlock.serializer(), it) },
                    fromChild = payload.string("senderSessionId") == id, append = false,
                )
            }
            "session_message_chunk" -> {
                val content = payload["content"] ?: return
                timeline.directedMessage(
                    messageId, listOf(ACPJson.decodeFromJsonElement(ContentBlock.serializer(), content)),
                    fromChild = payload.string("senderSessionId") == id, append = true,
                )
            }
        }
    }

    fun title(id: String): String = children[id]?.title ?: "Субагент"

    fun disconnect() {
        children.values.forEach { it.activity = Activity.Unknown }
        publish()
    }

    private fun publish() {
        snapshots.value = children.map { (id, child) ->
            Subagent(id, child.parentId, child.title, child.description, child.activity, child.timeline.items)
        }
    }
}

internal fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
