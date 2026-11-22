package io.github.aequicor.heartbeat.features.aistudio.ui

import io.github.aequicor.heartbeat.features.aistudio.component.ProjectsSessionsComponent

internal open class TestProjectsSessionsComponent : ProjectsSessionsComponent {
    override fun selectSession(id: String) = Unit
    override fun search(query: String) = Unit
    override fun setSearchVisible(visible: Boolean) = Unit
    override fun setPanelVisible(visible: Boolean) = Unit
    override fun setProjectExpanded(id: String, expanded: Boolean) = Unit
    override fun setSessionExpanded(id: String, expanded: Boolean) = Unit
    override fun loadMoreProjects() = Unit
    override fun loadMoreSessions(projectId: String) = Unit
    override fun loadMoreChildren(parentId: String) = Unit
    override fun retryPage(rowId: String) = Unit
}
