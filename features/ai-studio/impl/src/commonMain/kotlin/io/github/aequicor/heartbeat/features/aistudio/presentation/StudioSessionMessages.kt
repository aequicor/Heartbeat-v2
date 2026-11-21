package io.github.aequicor.heartbeat.features.aistudio.presentation

import io.github.aequicor.heartbeat.features.aiengine.api.session.Session
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionContent
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionEvent
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionState
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionStatus
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionTimelineItem
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Activity
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.User

internal fun Session.acceptsPrompt(): Boolean = when (state.value.status) {
    SessionStatus.Idle, is SessionStatus.Failed -> true
    else -> false
}

internal fun Session.answer(action: AiStudioAction.AnswerPermission) {
    val pending = state.value.status as? SessionStatus.AwaitingPermission ?: return
    val request = pending.requests.find { it.requestId == action.requestId } ?: return
    val kind = if (action.allow) "allow_once" else "reject_once"
    val option = requireNotNull(request.options.find { it.kind == kind }) { "Движок не предлагает этот вариант" }
    dispatch(SessionEvent.AnswerPermission(request.requestId, option.id))
}

internal fun AiStudioScreenState.withSession(state: SessionState): AiStudioScreenState {
    val permission = (state.status as? SessionStatus.AwaitingPermission)?.requests?.first()
    val messages = state.messages.items.asReversed().mapNotNull { item ->
        when (item) {
            is SessionTimelineItem.UserMessage -> User(item.id, "", item.content.text())
            is SessionTimelineItem.AgentMessage ->
                Reply(item.id, "", text = item.content.text(), status = ReplyStatus.Completed)
            else -> null
        }
    }
    return copy(
        messages = messages,
        activity = when (state.status) {
            SessionStatus.Running -> Activity.Running
            is SessionStatus.AwaitingPermission -> Activity.Waiting
            else -> Activity.Idle
        },
        request = permission?.let { AiStudioScreenState.Request.Permission(it.requestId, it.description, "", "", "") },
        storageError = when (val status = state.status) {
            is SessionStatus.Failed -> status.reason
            SessionStatus.Unavailable -> "Соединение с движком потеряно"
            else -> storageError
        },
    )
}

private fun List<SessionContent>.text(): String = joinToString("") {
    when (it) {
        is SessionContent.Text -> it.text
        is SessionContent.Resource -> it.title ?: it.uri
    }
}
