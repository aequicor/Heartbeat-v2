@file:Suppress("PackageNaming")

package io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions

import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Chat

internal sealed interface ProjectsSessionsAction {

    sealed interface ProjectsSessions : ProjectsSessionsAction {
        data class Search(val query: String) : ProjectsSessions
        data class ShowSearch(val visible: Boolean) : ProjectsSessions
        data class ShowPanel(val visible: Boolean) : ProjectsSessions
        data class FocusHandled(val id: String) : ProjectsSessions
    }

    sealed interface Project : ProjectsSessionsAction {
        data class Toggle(val name: String) : Project
        data class ShowAllSessions(val name: String) : Project
        data class CreateSession(val session: Chat) : Project
    }

    sealed interface Session : ProjectsSessionsAction {
        data class Toggle(val id: String) : Session
        data class Archive(val id: String) : Session
        data object Opened : Session
    }
}
