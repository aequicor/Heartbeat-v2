package io.github.aequicor.heartbeat.features.aistudio.presentation

import io.github.aequicor.heartbeat.features.aistudio.data.StudioCatalog
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.AuroraChat
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.AuroraNavigation
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.AuroraWorkspace
import kotlinx.serialization.json.Json

internal fun StudioCatalog.load(workspace: AuroraWorkspace): AuroraWorkspace {
    val (projects, rows) = read()
    val current = workspace.chats.associateBy { it.summary.id }
    val chats = rows.map { row ->
        val saved = current[row.id]
        val name = projects[row.project_id] ?: AuroraNavigation.Ungrouped
        val summary = (saved?.summary ?: Chat(row.id, row.title, name)).copy(
            title = row.title, project = name, projectId = row.project_id,
            engineId = row.engine_id, nativeId = row.native_id, parentId = row.parent_id,
        )
        saved?.copy(summary = summary) ?: AuroraChat(summary)
    }
    val archived = rows.filter { it.archived != 0L }.map { it.id }.toSet()
    return workspace.copy(chats = chats, projects = projects, archivedChats = archived)
}

internal fun StudioCatalog.importWorkspace(workspace: AuroraWorkspace) = transaction {
    workspace.chats.forEach { chat ->
        val summary = chat.summary
        val project = summary.project.takeUnless { it == AuroraNavigation.Ungrouped }
        importLocal(
            summary.id, summary.title, project, Json.encodeToString(chat),
            archived = summary.id in workspace.archivedChats, parent = summary.parentId,
        )
    }
}
