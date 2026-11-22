package io.github.aequicor.heartbeat.features.aistudio.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsAction.Panel
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsAction.Project
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsAction.Session
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

interface ProjectsSessionsComponent {
    fun selectSession(id: String)
    fun search(query: String)
    fun setSearchVisible(visible: Boolean)
    fun setPanelVisible(visible: Boolean)
    fun setProjectExpanded(id: String, expanded: Boolean)
    fun setSessionExpanded(id: String, expanded: Boolean)
    fun loadMoreProjects()
    fun loadMoreSessions(projectId: String)
    fun loadMoreChildren(parentId: String)
    fun retryPage(rowId: String)
}

internal class DefaultProjectsSessionsComponent(
    componentContext: ComponentContext,
    private val store: ProjectsSessionsStore,
    parentScope: CoroutineScope,
) : ProjectsSessionsComponent, ComponentContext by componentContext {
    private val job = Job(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + job)

    init {
        lifecycle.doOnDestroy(job::cancel)
    }

    override fun selectSession(id: String) = dispatch(Session.Select(id))

    override fun search(query: String) = dispatch(Panel.Search(query))

    override fun setSearchVisible(visible: Boolean) =
        dispatch(if (visible) Panel.ExpandSearch else Panel.CollapseSearch)

    override fun setPanelVisible(visible: Boolean) =
        dispatch(if (visible) Panel.ExpandPanel else Panel.CollapsePanel)

    override fun setProjectExpanded(id: String, expanded: Boolean) = dispatch(Project.SetExpanded(id, expanded))

    override fun setSessionExpanded(id: String, expanded: Boolean) = dispatch(Session.SetExpanded(id, expanded))

    override fun loadMoreProjects() = dispatch(Panel.LoadMoreProjects)

    override fun loadMoreSessions(projectId: String) = dispatch(Project.LoadMoreSessions(projectId))

    override fun loadMoreChildren(parentId: String) = dispatch(Session.LoadMoreChildren(parentId))

    override fun retryPage(rowId: String) = dispatch(Panel.RetryPage(rowId))

    private fun dispatch(action: ProjectsSessionsAction) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            store.dispatch(action)
        }
    }
}
