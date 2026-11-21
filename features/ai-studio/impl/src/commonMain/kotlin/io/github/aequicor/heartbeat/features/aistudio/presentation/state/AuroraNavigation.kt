package io.github.aequicor.heartbeat.features.aistudio.presentation.state

import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Activity
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationKind
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationRow
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationStatus

internal object AuroraNavigation {
    const val Ungrouped = "Без проекта"
    const val ProjectPrefix = "project:"
    const val MorePrefix = "more:"
}
private const val SessionLimit = 5

/** Computes visible rows before Compose measures the navigation tree. */
internal fun AuroraWorkspace.navigation(
    query: String, activity: Map<String, Activity>,
): List<NavigationRow> = buildList {
    val chatsByProject = activeChats().map(AuroraChat::summary).map { chat ->
        if (chat.parentId in archivedChats) chat.copy(parentId = null) else chat
    }.groupBy(Chat::project)
    val ungrouped = AuroraNavigation.Ungrouped
    val projects = chatsByProject.keys.filterNot { it == ungrouped } +
        listOfNotNull(ungrouped.takeIf { it in chatsByProject })
    projects.forEach { project ->
        val rootId = AuroraNavigation.ProjectPrefix + project
        val expanded = query.isNotEmpty() || rootId !in collapsedNavigation
        val sessions = chatsByProject.getValue(project)
        val filtered = sessions.visibleIn(project, query)
        if (filtered.isEmpty()) return@forEach

        val kind = if (project == AuroraNavigation.Ungrouped) NavigationKind.Section else NavigationKind.Project
        add(NavigationRow(rootId, project, kind, project, expanded = expanded))
        if (expanded) addSessions(filtered, project, query, activity, this@navigation)
    }
}

private fun List<Chat>.visibleIn(project: String, query: String): List<Chat> {
    if (project.contains(query, ignoreCase = true)) return this
    val matching = filter { it.title.contains(query, ignoreCase = true) }
    val parents = matching.mapNotNull(Chat::parentId).toSet()
    return filter { it in matching || it.id in parents }
}

private fun MutableList<NavigationRow>.addSessions(
    sessions: List<Chat>,
    project: String,
    query: String,
    activity: Map<String, Activity>,
    workspace: AuroraWorkspace,
) {
    val roots = sessions.filter { it.parentId == null }
    val children = sessions.filter { it.parentId != null }.groupBy(Chat::parentId)
    val showAll = query.isNotEmpty() || project in workspace.expandedProjects || project == AuroraNavigation.Ungrouped
    val visible = if (showAll) roots else roots.take(SessionLimit)
    for (chat in visible) {
        val branch = children[chat.id].orEmpty()
        val open = query.isNotEmpty() || chat.id !in workspace.collapsedNavigation
        val expandable = open.takeIf { branch.isNotEmpty() }
        add(chat.row(workspace.selectedId, activity, expanded = expandable))
        if (open) branch.forEachIndexed { index, child ->
            add(child.row(workspace.selectedId, activity, depth = 1, lastChild = index == branch.lastIndex))
        }
    }
    if (!showAll && roots.size > SessionLimit) {
        add(NavigationRow(AuroraNavigation.MorePrefix + project, "Ещё диалоги", NavigationKind.More, project))
    }
}

private fun Chat.row(
    selectedId: String,
    activity: Map<String, Activity>,
    expanded: Boolean? = null,
    depth: Int = 0,
    lastChild: Boolean = false,
): NavigationRow {
    val badge = when (activity[id]) {
        Activity.Waiting -> NavigationStatus.Attention
        Activity.Running, Activity.Sending, Activity.Stopping -> NavigationStatus.Working
        else -> navigationStatus
    }
    return NavigationRow(id, title, NavigationKind.Chat, project, depth, expanded, id == selectedId, badge, lastChild)
}
