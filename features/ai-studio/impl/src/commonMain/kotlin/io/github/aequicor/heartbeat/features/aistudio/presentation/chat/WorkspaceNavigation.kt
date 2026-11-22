package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.NavigationKind
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.NavigationStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsState

internal fun AuroraWorkspace.withProjectsSessions(
    screen: AiStudioScreenState,
    navigation: ProjectsSessionsState,
): AiStudioScreenState {
    val next = navigation.applyTo(screen, activeChats())
    val chatsById = chats.associate { it.summary.id to it.summary }
    val rows = next.navigation.map { row ->
        val chat = chatsById[row.id]
        if (row.kind != NavigationKind.Chat || chat == null) row
        else row.copy(
            title = if (navigation.query.isNotBlank()) row.title else chat.title,
            status = chat.navigationStatus,
        )
    }
    return next.copy(navigation = rows)
}

internal fun AuroraWorkspace.deletedChatIds(matches: (AuroraChat) -> Boolean): Set<String> {
    val removed = chats.filter(matches).map { it.summary.id }.toMutableSet()
    val children = chats.groupBy { it.summary.parentId }
    val queue = ArrayDeque(removed)
    while (queue.isNotEmpty()) {
        for (child in children[queue.removeFirst()].orEmpty()) {
            if (removed.add(child.summary.id)) queue.addLast(child.summary.id)
        }
    }
    return removed
}

internal fun AuroraWorkspace.withoutChats(removed: Set<String>): AuroraWorkspace = copy(
    selectedId = if (selectedId in removed) "" else selectedId,
    chats = chats.filterNot { it.summary.id in removed },
    archivedChats = archivedChats - removed,
    collapsedNavigation = collapsedNavigation - removed,
)

internal fun AuroraWorkspace.renameProject(id: String, title: String): AuroraWorkspace =
    copy(chats = chats.map { chat ->
        if (chat.summary.projectId == id) chat.copy(summary = chat.summary.copy(project = title))
        else chat
    })

internal fun AuroraWorkspace.subagents(chatId: String): List<AiStudioScreenState.Subagent> = chats.mapNotNull {
    val child = it.summary
    if (child.parentId == chatId && child.mockSubagent) {
        AiStudioScreenState.Subagent(child.id, child.title)
    } else null
}

internal fun AiStudioScreenState.collapseWork(replyId: String): AiStudioScreenState {
    val prefix = "$replyId/"
    return copy(
        expandedWork = expandedWork - replyId,
        expandedSteps = expandedSteps.filterNot { it.startsWith(prefix) }.toSet(),
        expandedEvents = expandedEvents.filterNot { it.startsWith(prefix) }.toSet(),
    )
}

internal fun AuroraChat.withReadResult(): AuroraChat =
    if (summary.navigationStatus == NavigationStatus.Unread) copy(summary = summary.copy(navigationStatus = null))
    else this
