@file:Suppress("PackageNaming")

package io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions

import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationKind
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationRow
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationStatus

internal data class ProjectsSessionsState(
    val panel: Panel = Panel(),
    val projects: List<Project> = listOf(),
) {

    data class Panel(
        val query: String = "",
        val searchVisible: Boolean = false,
        val visible: Boolean = false,
        val focusId: String? = null,
    )

    data class Project(
        val id: String,
        val name: String,
        val kind: NavigationKind,
        val expanded: Boolean,
        val sessionLimit: Int?,
        val sessions: List<Session>,
    )

    data class Session(
        val chat: Chat,
        val position: Int,
        val matches: Boolean,
        val selected: Boolean,
        val status: NavigationStatus?,
        val expanded: Boolean,
        val children: List<Session>,
    )

    fun applyTo(screen: AiStudioScreenState): AiStudioScreenState = screen.copy(
        chatSearch = panel.query,
        chatSearchVisible = panel.searchVisible,
        chatsVisible = panel.visible,
        chats = projects.asSequence().flatMap { it.sessions }
            .flatMap { sequenceOf(it) + it.children }
            .filter(Session::matches)
            .sortedBy(Session::position)
            .map(Session::chat)
            .toList(),
        navigation = rows(),
        navigationFocusId = panel.focusId,
    )

    fun rows(): List<NavigationRow> = projects.flatMap { it.rows() }

    private fun Project.rows(): List<NavigationRow> = buildList {
        val matches = sessions.filter { it.matches || it.children.any(Session::matches) }
        if (matches.isEmpty()) return@buildList
        add(NavigationRow(id, name, kind, name, expanded = expanded))
        if (expanded) {
            val visible = sessionLimit?.let(matches::take) ?: matches
            for (session in visible) {
                val children = session.children.filter(Session::matches)
                add(session.row().copy(expanded = session.expanded.takeIf { children.isNotEmpty() }))
                if (session.expanded) children.forEachIndexed { index, child ->
                    add(child.row().copy(depth = 1, lastChild = index == children.lastIndex))
                }
            }
            if (visible.size < matches.size) add(NavigationRow("more:$name", "Ещё диалоги", NavigationKind.More, name))
        }
    }

    private fun Session.row(): NavigationRow = NavigationRow(
        id = chat.id,
        title = chat.title,
        kind = NavigationKind.Chat,
        project = chat.project,
        selected = selected,
        status = status,
    )
}
