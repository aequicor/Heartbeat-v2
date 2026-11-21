package io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.StudioSessionId
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions.model.ListFocusTarget
import io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions.model.ProjectListItem
import io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions.model.SavedProjectsSessions
import io.github.aequicor.heartbeat.features.aistudio.presentation.projects_sessions.model.SessionListItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

internal class ProjectsSessionsBlocTest {

    private val child =
        SessionListItem(StudioSessionId(Uuid.random()), "Child", activityBadge = NavigationStatus.Working)
    private val root = SessionListItem(StudioSessionId(Uuid.random()), "Root", children = listOf(child))
    private val project = ProjectListItem(ProjectId(Uuid.random()), "Project", listOf(root))

    @Test
    fun searchRevealsMatchingChildAndRestoresCollapsedBranchWhenClosed() {
        val bloc = ProjectsSessionsBloc(SavedProjectsSessions()) {}
        bloc.updateProjects(listOf(project), root.id)
        bloc.dispatch(ProjectsSessionsEvent.SessionToggled(root.id))
        bloc.dispatch(ProjectsSessionsEvent.SearchVisibilityChanged(true))
        bloc.dispatch(ProjectsSessionsEvent.SearchChanged("CHILD"))

        val parent = bloc.state.value.projects.single().sessions.single()
        assertFalse(parent.matchesSearch)
        assertTrue(parent.expanded)
        assertEquals(child, parent.children.single())

        bloc.dispatch(ProjectsSessionsEvent.SearchVisibilityChanged(false))
        assertFalse(bloc.state.value.projects.single().sessions.single().expanded)
    }

    @Test
    fun screenConfirmsSelectionAndArchivingParentPromotesChildInProject() {
        val opened = mutableListOf<StudioSessionId>()
        val bloc = ProjectsSessionsBloc(SavedProjectsSessions(), opened::add)
        bloc.updateProjects(listOf(project), root.id)
        bloc.dispatch(ProjectsSessionsEvent.SessionClicked(child.id))

        assertEquals(listOf(child.id), opened)
        assertTrue(bloc.state.value.projects.single().sessions.single().selected)
        bloc.updateProjects(listOf(project), child.id)
        bloc.dispatch(ProjectsSessionsEvent.ArchiveSessionRequested(root.id))

        val result = bloc.state.value
        assertEquals(project.id, result.projects.single().id)
        assertEquals(child.copy(selected = true), result.projects.single().sessions.single())
        assertEquals(ListFocusTarget.Session(child.id), result.focus)

        bloc.dispatch(ProjectsSessionsEvent.ArchiveSessionRequested(child.id))
        val emptyProject = bloc.state.value.projects.single()
        assertEquals(project.id, emptyProject.id)
        assertTrue(emptyProject.sessions.isEmpty())
    }

    @Test
    fun projectsWithTheSameTitleExpandIndependentlyAndMayBeEmpty() {
        val empty = project.copy(id = ProjectId(Uuid.random()), sessions = listOf())
        val bloc = ProjectsSessionsBloc(SavedProjectsSessions()) {}
        bloc.updateProjects(listOf(project, empty), child.id)
        bloc.dispatch(ProjectsSessionsEvent.ProjectToggled(project.id))

        val projects = bloc.state.value.projects
        assertEquals(listOf(project.id, empty.id), projects.map { it.id })
        assertFalse(projects.first().expanded)
        assertTrue(projects.last().expanded)
        assertTrue(projects.last().sessions.isEmpty())
    }
}
