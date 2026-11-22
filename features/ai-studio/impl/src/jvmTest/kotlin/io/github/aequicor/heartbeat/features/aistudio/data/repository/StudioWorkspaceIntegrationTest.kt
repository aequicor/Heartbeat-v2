package io.github.aequicor.heartbeat.features.aistudio.data.repository

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.zacsweers.metro.createGraphFactory
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStoreFactory
import io.github.aequicor.heartbeat.features.aistudio.di.DefaultAiStudioStoreFactory
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Session as StoredSession
import io.github.aequicor.heartbeat.features.aistudio.presentation.DefaultAiStudioStore
import kotlinx.coroutines.test.TestScope
import io.github.aequicor.heartbeat.features.aistudio.data.workspace.StudioWorkspacePersistence
import io.github.aequicor.heartbeat.features.aistudio.db.HeartbeatDatabase
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Project
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectCreationResult
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectDirectory
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectsSessionsChange
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionWorkState
import io.github.aequicor.heartbeat.features.aistudio.presentation.TestPreferences
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.NavigationStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.SessionBootstrapState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioStore
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsAction.Panel
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsAction.Project as ProjectAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.ProjectsSessionsAction.Session
import io.github.aequicor.heartbeat.features.aistudio.presentation.projectssessions.ProjectsSessionsStoreImpl
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.AuroraChat
import io.github.aequicor.heartbeat.features.aistudio.presentation.state.AuroraWorkspace
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class StudioWorkspaceIntegrationTest {

    @Test
    fun importsPreferencesOnceAndKeepsTheSelectedHistoryAndDraft() = runTest {
        driver().use { driver ->
            val connection = connection(driver)
            val graph = graph(connection)
            val storage = StudioWorkspacePersistence(connection)
            val legacy = legacyWorkspace()
            val preferences = TestPreferences(Json.encodeToString(legacy))
            val store = store(preferences, graph, storage)
            runCurrent()

            val id = store.screenState.value.navigation.single { it.selected }.id
            assertNull(store.screenState.value.chat)
            openChat(store, id)
            assertTrue(id != legacy.selectedId)
            assertEquals("saved draft", store.draft.value.text)
            assertEquals("saved message", (store.screenState.value.messages.first() as AiStudioScreenState.User).text)
            assertEquals(legacy.chats.single().anchor, store.screenState.value.restoreAnchor)
            val projects = graph.catalog.listProjects(ProjectStatus.ACTIVE, limit = 100).items
            assertEquals(Project.WithoutDirectory(isExpanded = true), projects.single())
            assertEquals("Без проекта", store.screenState.value.chat?.project)
            assertEquals(ProjectId.Ungrouped, graph.catalog.findSession(SessionId(Uuid.parse(id)))?.projectId)

            store.dispatch(AiStudioAction.EditDraft(id, "updated draft"))
            advanceTimeBy(175)
            runCurrent()
            preferences.snapshot.value = "unreadable legacy snapshot"
            val restarted = store(preferences, graph, storage)
            runCurrent()
            openChat(restarted, id)
            assertEquals(id, restarted.screenState.value.chat?.id)
            assertEquals("updated draft", restarted.draft.value.text)
            assertEquals(projects, graph.catalog.listProjects(ProjectStatus.ACTIVE, limit = 100).items)
        }
    }

    @Test
    fun newChatWithNoSelectionUsesTheSystemProjectAndSavesItsFirstPromptTitle() = runTest {
        driver().use { driver ->
            val connection = connection(driver)
            val graph = graph(connection)
            graph.sessionBootstrap.registerProject(ProjectDirectory.Local("/work/Alpha".toPath()))
            val storage = StudioWorkspacePersistence(connection)
            storage.importEmptyWorkspace()
            val store = store(TestPreferences(), graph, storage)
            runCurrent()
            assertNull(store.screenState.value.chat)

            store.dispatch(AiStudioAction.NewChat)
            runCurrent()

            val id = store.screenState.value.navigation.single { it.selected }.id
            assertNull(store.screenState.value.chat)
            openChat(store, id)
            val chat = assertNotNull(store.screenState.value.chat)
            val sessionId = SessionId.parse(chat.id)
            assertEquals(ProjectId.Ungrouped.value.toString(), chat.projectId)
            assertEquals(ProjectId.Ungrouped, graph.catalog.findSession(sessionId)?.projectId)

            store.dispatch(AiStudioAction.EditDraft(chat.id, "First prompt"))
            store.dispatch(AiStudioAction.Send)
            runCurrent()

            assertEquals("First prompt", graph.catalog.findSession(sessionId)?.title)
        }
    }

    @Test
    fun newChatIsAddedBeforeTheLoadedSessionsAndKeepsTheNextPage() = runTest {
        driver().use { driver ->
            val connection = connection(driver)
            val graph = graph(connection)
            repeat(6) {
                graph.repository.createSession(ProjectId.Ungrouped, "Session $it", Instant.DISTANT_PAST)
            }
            val storage = StudioWorkspacePersistence(connection)
            storage.importEmptyWorkspace()
            val store = createStore(graph, storage)
            val projectsSessions = assertNotNull(store.projectsSessionsStore)
            runCurrent()

            val before = store.screenState.value.navigation.filter {
                it.kind == AiStudioScreenState.NavigationKind.Chat
            }
            assertEquals(5, before.size)

            store.dispatch(AiStudioAction.NewChat)
            runCurrent()

            val state = store.screenState.value
            assertNull(state.chat)
            val created = state.navigation.single { it.selected }
            val rows = state.navigation.filter { it.kind == AiStudioScreenState.NavigationKind.Chat }
            assertEquals(listOf(created.id) + before.map { it.id }, rows.map { it.id })
            val more = state.navigation.single { it.kind == AiStudioScreenState.NavigationKind.More }
            projectsSessions.dispatch(ProjectAction.LoadMoreSessions(more.project)).getOrThrow()
            runCurrent()
            val allRows = store.screenState.value.navigation.filter {
                it.kind == AiStudioScreenState.NavigationKind.Chat
            }
            assertEquals(7, allRows.map { it.id }.distinct().size)
        }
    }

    @Test
    fun archiveChatUpdatesTheStoredSessionAndRemovesItsNavigationRow() = runTest {
        driver().use { driver ->
            val connection = connection(driver)
            val graph = graph(connection)
            val storage = StudioWorkspacePersistence(connection)
            storage.importEmptyWorkspace()
            val store = store(TestPreferences(), graph, storage)
            runCurrent()

            store.dispatch(AiStudioAction.NewChat)
            runCurrent()
            val selected = store.screenState.value.navigation.single { it.selected }

            store.dispatch(AiStudioAction.ArchiveChat(selected.id))
            runCurrent()

            assertEquals(SessionStatus.ARCHIVED, graph.catalog.findSession(SessionId.parse(selected.id))?.status)
            assertFalse(store.screenState.value.navigation.any { it.id == selected.id })
        }
    }

    @Test
    fun sessionsWithoutAProjectUseTheSameProjectAsImportedChats() = runTest {
        driver().use { driver ->
            val connection = connection(driver)
            val graph = graph(connection)
            val legacy = legacyWorkspace(project = "Без проекта")
            val store = store(
                TestPreferences(Json.encodeToString(legacy)), graph, StudioWorkspacePersistence(connection),
            )
            runCurrent()
            val imported = graph.catalog.listRootSessions(ProjectId.Ungrouped, SessionStatus.ACTIVE).items.single()
            val systemId = ProjectId.Ungrouped.value.toString()
            assertEquals(ProjectId.Ungrouped, imported.projectId)
            val ungrouped = Project.WithoutDirectory(isExpanded = false)
            assertEquals(ungrouped, graph.catalog.listProjects(ProjectStatus.ACTIVE).items.single())
            assertEquals(ungrouped, graph.projectDataSource.findById(ProjectId.Ungrouped))

            graph.sessionBootstrap.registerProject(ProjectDirectory.Local("/work/Без проекта".toPath()))
            store.dispatch(AiStudioAction.CreateSessionWithoutProject)
            runCurrent()
            val target = assertNotNull(store.screenState.value.sessionBootstrap).project
            store.dispatch(AiStudioAction.SubmitSessionBootstrap(target, "Continue separately"))
            runCurrent()

            val created = graph.catalog.listRootSessions(ProjectId.Ungrouped, SessionStatus.ACTIVE)
                .items.single { it.id != imported.id }
            assertEquals(ProjectId.Ungrouped, created.projectId)
            assertEquals(created.id.value.toString(), store.screenState.value.chat?.id)
            val rows = store.screenState.value.navigation.filter { it.title == "Без проекта" }
            assertEquals("project:$systemId", rows.single { it.kind == AiStudioScreenState.NavigationKind.Section }.id)
            assertTrue(rows.any { it.kind == AiStudioScreenState.NavigationKind.Project })
        }
    }

    @Test
    fun renamesTheSelectedSessionAndItsProjectInSqlDelight() = runTest {
        driver().use { driver ->
            val connection = connection(driver)
            val graph = graph(connection)
            val storage = StudioWorkspacePersistence(connection)
            storage.importEmptyWorkspace()
            val originalProject = assertIs<ProjectCreationResult.Registered>(
                graph.catalog.createProject("/work/Original".toPath()),
            ).project
            val seed = legacyWorkspace(originalProject.title).chats.single()
            val session = graph.repository.createSession(
                originalProject.id, seed.summary.title, Instant.DISTANT_PAST,
            )
            val projectId = originalProject.id.value.toString()
            val id = session.id.value.toString()
            val persistedChat = seed.copy(summary = seed.summary.copy(id = id, projectId = projectId))
            storage.save(AuroraWorkspace(id, listOf(persistedChat)))
            val preferences = TestPreferences()
            val store = store(preferences, graph, storage)
            runCurrent()

            openChat(store, id)
            val chat = assertNotNull(store.screenState.value.chat)
            store.dispatch(AiStudioAction.EditDraft(chat.id, "updated draft"))
            advanceTimeBy(175)
            runCurrent()
            val snapshot = storage.restore { error("Workspace was already imported") }
            val savedChat = storage.load(snapshot.chats.single { it.summary.id == chat.id })

            store.dispatch(AiStudioAction.RenameProject(projectId, "Renamed project"))
            store.dispatch(AiStudioAction.RenameSession(chat.id, "Renamed session"))
            runCurrent()

            val screen = store.screenState.value
            assertEquals(chat.copy(title = "Renamed session", project = "Renamed project"), screen.chat)
            assertEquals("Renamed project", screen.navigation.single { it.id == "project:$projectId" }.title)
            assertEquals("Renamed session", screen.navigation.single { it.id == chat.id }.title)

            store.dispatch(AiStudioAction.RenameSession(chat.id, " "))
            runCurrent()
            assertEquals(AiStudioScreenState.RenameError.Session, store.screenState.value.renameError)

            val project = assertIs<Project.WithDirectory>(graph.projectDataSource.findById(originalProject.id))
            assertEquals(originalProject.immutableName, project.immutableName)
            assertEquals("Renamed project", project.title)
            storage.save(snapshot.copy(chats = listOf(savedChat)))
            val restarted = store(preferences, graph, storage)
            runCurrent()
            openChat(restarted, id)
            assertEquals(screen.chat, restarted.screenState.value.chat)
            assertEquals("updated draft", restarted.draft.value.text)
            assertEquals(screen.messages, restarted.screenState.value.messages)
        }
    }

    @Test
    fun failedImportLeavesPreferencesIntactAndCanRetry() = runTest {
        driver().use { driver ->
            val connection = connection(driver)
            val graph = graph(connection)
            val storage = StudioWorkspacePersistence(connection)
            val original = Json.encodeToString(legacyWorkspace())
            val preferences = TestPreferences(original)
            driver.execute(
                null,
                """
                CREATE TRIGGER reject_import BEFORE INSERT ON studio_session
                BEGIN SELECT RAISE(ABORT, 'test rejection'); END
                """.trimIndent(),
                0,
            )

            val failed = store(preferences, graph, storage)
            runCurrent()
            assertNotNull(failed.screenState.value.storageError)
            assertNull(HeartbeatDatabase(driver).studioWorkspaceQueries.selectWorkspaceMetadata().executeAsOneOrNull())
            assertEquals(
                Project.WithoutDirectory(isExpanded = true),
                graph.catalog.listProjects(ProjectStatus.ACTIVE).items.single(),
            )
            assertEquals(original, preferences.snapshot.value)

            driver.execute(null, "DROP TRIGGER reject_import", 0)
            val restored = store(preferences, graph, storage)
            runCurrent()
            assertNull(restored.screenState.value.storageError)
            openChat(restored, restored.screenState.value.navigation.single { it.selected }.id)
            assertEquals("saved draft", restored.draft.value.text)
            val metadata = HeartbeatDatabase(driver).studioWorkspaceQueries.selectWorkspaceMetadata()
            assertNotNull(metadata.executeAsOneOrNull())
        }
    }

    @Test
    fun loadsIndependentProjectRootAndChildPagesFromSqlDelight() = runTest {
        driver().use { driver ->
            val connection = connection(driver)
            val graph = graph(connection)
            val storage = StudioWorkspacePersistence(connection)
            storage.importEmptyWorkspace()
            val root = seedPagedProjects(graph)
            val id = root.projectId.value.toString()

            val store = createStore(graph, storage)
            val projectsSessions = assertNotNull(store.projectsSessionsStore)
            runCurrent()
            val before = store.screenState.value.navigation
            assertEquals(20, before.count { it.kind == AiStudioScreenState.NavigationKind.Project })
            assertEquals(5, before.count { it.kind == AiStudioScreenState.NavigationKind.Chat && it.depth == 0 })
            assertEquals(5, before.count { it.kind == AiStudioScreenState.NavigationKind.Chat && it.depth == 1 })
            assertTrue(before.any { it.id == "more-projects" })
            assertTrue(before.any { it.id == "more:$id" })
            val childCursor = "more-children:${root.id.value}"
            assertTrue(before.any { it.id == childCursor })

            projectsSessions.dispatch(Session.LoadMoreChildren(root.id.value.toString())).getOrThrow()
            runCurrent()
            val afterChildren = store.screenState.value.navigation
            assertEquals(6, afterChildren.count { it.depth == 1 })
            assertTrue(afterChildren.any { it.id == "more:$id" })

            projectsSessions.dispatch(ProjectAction.LoadMoreSessions(id)).getOrThrow()
            repeat(2) {
                launch(start = CoroutineStart.UNDISPATCHED) {
                    projectsSessions.dispatch(Panel.LoadMoreProjects).getOrThrow()
                }
            }
            runCurrent()
            val secondPage = store.screenState.value.navigation
            assertEquals(40, secondPage.count { it.kind == AiStudioScreenState.NavigationKind.Project })
            assertTrue(secondPage.any { it.kind == AiStudioScreenState.NavigationKind.MoreProjects })

            projectsSessions.dispatch(Panel.LoadMoreProjects).getOrThrow()
            runCurrent()
            val rows = store.screenState.value.navigation
            assertEquals(41, rows.count { it.kind == AiStudioScreenState.NavigationKind.Project })
            assertEquals(6, rows.count { it.kind == AiStudioScreenState.NavigationKind.Chat && it.depth == 0 })
            assertFalse(rows.any { it.kind == AiStudioScreenState.NavigationKind.MoreProjects })

            val rootId = root.id.value.toString()
            projectsSessions.dispatch(Session.SetExpanded(rootId, false)).getOrThrow()
            runCurrent()
            assertEquals(false, graph.catalog.findSession(root.id)?.isExpanded)
            assertFalse(store.screenState.value.navigation.any { it.depth == 1 })

            projectsSessions.dispatch(Session.SetExpanded(rootId, true)).getOrThrow()
            runCurrent()
            assertEquals(true, graph.catalog.findSession(root.id)?.isExpanded)
            assertEquals(rows, store.screenState.value.navigation)

            projectsSessions.dispatch(ProjectAction.SetExpanded(id, false)).getOrThrow()
            runCurrent()
            assertEquals(false, graph.projectDataSource.findById(root.projectId)?.isExpanded)
            assertFalse(store.screenState.value.navigation.any { it.id == rootId })

            projectsSessions.dispatch(ProjectAction.SetExpanded(id, true)).getOrThrow()
            runCurrent()
            assertEquals(true, graph.projectDataSource.findById(root.projectId)?.isExpanded)
            assertEquals(rows, store.screenState.value.navigation)
        }
    }

    @Test
    fun searchesProjectsAndNestedSessionsAcrossPages() = runTest {
        driver().use { driver ->
            val connection = connection(driver)
            val graph = graph(connection)
            val storage = StudioWorkspacePersistence(connection)
            storage.importEmptyWorkspace()
            val project = assertIs<ProjectCreationResult.Registered>(
                graph.catalog.createProject("/research".toPath(), name = "Research"),
            ).project
            val root = graph.repository.createSession(project.id, "Root", Instant.DISTANT_PAST)
            val child = graph.repository.createSession(project.id, "Branch", Instant.DISTANT_PAST, root.id)
            val match = graph.repository.createSession(project.id, "Квантовый датчик", Instant.DISTANT_PAST, child.id)
            repeat(6) { graph.repository.createSession(project.id, "Other root $it") }
            val archived = graph.repository.createSession(project.id, "Квантовый архив")
            graph.catalog.setSessionArchived(archived, isArchived = true, changedAt = archived.createdAt)
            val archivedProject = assertIs<ProjectCreationResult.Registered>(
                graph.catalog.createProject("/archived".toPath(), name = "Квантовый проект"),
            ).project
            graph.catalog.setProjectArchived(archivedProject, isArchived = true)

            val matchTree = graph.catalog.search("  КВАНТОВЫЙ  ").single()
            assertEquals(project, matchTree.project)
            val rootNode = matchTree.sessions.single()
            assertEquals(root, rootNode.session)
            val childNode = rootNode.children.single()
            assertEquals(child, childNode.session)
            assertEquals(match, childNode.children.single().session)
            assertEquals(7, graph.catalog.search("RESEARCH").single().sessions.size)

            val emptyProject = assertIs<ProjectCreationResult.Registered>(
                graph.catalog.createProject("/literal".toPath(), name = "Наука [B?*]"),
            ).project
            val literalMatch = graph.catalog.search("[b?*]").single()
            assertEquals(emptyProject, literalMatch.project)
            assertTrue(literalMatch.sessions.isEmpty())

            val store = createStore(graph, storage)
            val projectsSessions = assertNotNull(store.projectsSessionsStore)
            runCurrent()
            projectsSessions.dispatch(Panel.ExpandSearch).getOrThrow()
            projectsSessions.dispatch(Panel.Search("квантовый")).getOrThrow()
            runCurrent()
            assertTrue(store.screenState.value.chatSearchVisible)
            assertEquals(
                listOf(root.id, child.id, match.id).map { it.value.toString() },
                store.screenState.value.navigation
                    .filter { it.kind == AiStudioScreenState.NavigationKind.Chat }
                    .map { it.id },
            )

            projectsSessions.dispatch(Panel.Search("unmatched")).getOrThrow()
            runCurrent()
            assertTrue(store.screenState.value.navigation.isEmpty())

            projectsSessions.dispatch(Panel.CollapseSearch).getOrThrow()
            runCurrent()
            assertFalse(store.screenState.value.chatSearchVisible)
            assertTrue(store.screenState.value.navigation.any { it.kind == AiStudioScreenState.NavigationKind.More })
        }
    }

    @Test
    fun selectingAStoredSessionKeepsTheChatClosedAndTheResultUnread() = runTest {
        driver().use { driver ->
            val connection = connection(driver)
            val graph = graph(connection)
            val storage = StudioWorkspacePersistence(connection)
            storage.importEmptyWorkspace()
            val waiting = graph.repository.createSession(ProjectId.Ungrouped, "Waiting result")
            val interrupted = graph.repository.createSession(ProjectId.Ungrouped, "Interrupted work")
            val other = graph.repository.createSession(ProjectId.Ungrouped, "Other session")
            val waitingId = waiting.id.value.toString()
            val interruptedId = interrupted.id.value.toString()
            val project = Chat.UngroupedProjectKey
            storage.save(
                AuroraWorkspace(
                    selectedId = waitingId,
                    chats = listOf(
                        AuroraChat(Chat(waitingId, waiting.title, project, navigationStatus = NavigationStatus.Unread)),
                        AuroraChat(
                            Chat(
                                interruptedId, interrupted.title, project, navigationStatus = NavigationStatus.Working,
                            ),
                        ),
                    ),
                ),
            )
            assertEquals(SessionWorkState.Waiting, graph.catalog.findSession(waiting.id)?.workState)
            assertEquals(SessionWorkState.Working, graph.catalog.findSession(interrupted.id)?.workState)
            graph.catalog.setSessionArchived(interrupted, isArchived = true, changedAt = Instant.DISTANT_PAST)

            val store = createStore(graph, StudioWorkspacePersistence(connection))
            val projectsSessions = assertNotNull(store.projectsSessionsStore)
            runCurrent()
            assertEquals(
                NavigationStatus.Unread,
                store.screenState.value.navigation.single { it.id == waitingId }.status,
            )
            assertEquals(SessionWorkState.Idle, graph.catalog.findSession(interrupted.id)?.workState)
            assertEquals(SessionStatus.ARCHIVED, graph.catalog.findSession(interrupted.id)?.status)

            val otherId = other.id.value.toString()
            projectsSessions.dispatch(Session.Select(otherId)).getOrThrow()
            runCurrent()
            assertEquals(otherId, store.screenState.value.navigation.single { it.selected }.id)

            projectsSessions.dispatch(Session.Select(waitingId)).getOrThrow()
            runCurrent()
            assertEquals(waitingId, store.screenState.value.navigation.single { it.selected }.id)
            assertNull(store.screenState.value.chat)
            assertTrue(store.screenState.value.messages.isEmpty())
            assertEquals(SessionWorkState.Waiting, graph.catalog.findSession(waiting.id)?.workState)

            openChat(store, waitingId)
            assertEquals(SessionWorkState.Idle, graph.catalog.findSession(waiting.id)?.workState)
            assertEquals(SessionStatus.ACTIVE, graph.catalog.findSession(waiting.id)?.status)
            assertNull(store.screenState.value.navigation.single { it.id == waitingId }.status)
        }
    }

    @Test
    fun loadsChildrenOnlyForTheCurrentCursor() = runTest {
        driver().use { driver ->
            val graph = graph(connection(driver))
            val project = assertIs<ProjectCreationResult.Registered>(
                graph.catalog.createProject("/paged".toPath(), name = "Paged project"),
            ).project
            val time = Instant.parse("2026-11-21T17:00:00Z")
            val parent = graph.repository.createSession(project.id, "Parent", time)
            repeat(11) { graph.repository.createSession(project.id, "child $it", time, parent.id) }

            val store = ProjectsSessionsStoreImpl(graph.catalog, graph.observeProjectsSessions(), backgroundScope)
            store.dispatch(Panel.Load)
            val before = store.state.value
            val first = assertNotNull(before.pages.children[parent.id]?.nextCursor)
            val action = Session.LoadMoreChildren(parent.id.value.toString())

            repeat(2) {
                launch(start = CoroutineStart.UNDISPATCHED) { store.dispatch(action).getOrThrow() }
            }
            runCurrent()
            val second = assertNotNull(store.state.value.pages.children[parent.id]?.nextCursor)
            assertEquals(first, before.pages.children[parent.id]?.nextCursor)
            assertEquals(5, before.pages.children.getValue(parent.id).items.size)
            assertEquals(second, store.state.value.pages.children[parent.id]?.nextCursor)
            assertEquals(
                10, store.state.value.projects.single { it.name == project.title }.sessions.single().children.size,
            )

            store.dispatch(action).getOrThrow()
            assertNull(store.state.value.pages.children[parent.id]?.nextCursor)
            assertEquals(
                11, store.state.value.projects.single { it.name == project.title }.sessions.single().children.size,
            )
        }
    }

    @Test
    fun navigationUsesPersistedExpansionWithoutWorkspaceMetadata() = runTest {
        driver().use { driver ->
            val graph = graph(connection(driver))
            val project = assertIs<ProjectCreationResult.Registered>(
                graph.catalog.createProject("/nested".toPath(), name = "Nested project"),
            ).project
            val parent = graph.repository.createSession(project.id, "Parent")
            val child = graph.repository.createSession(project.id, "Child", Clock.System.now(), parent.id)
            val grandchild = graph.repository.createSession(project.id, "Grandchild", Clock.System.now(), child.id)
            graph.catalog.setSessionExpanded(child, false)

            val store = ProjectsSessionsStoreImpl(graph.catalog, graph.observeProjectsSessions(), backgroundScope)
            store.dispatch(Panel.Load)

            val childId = child.id.value.toString()
            val grandchildId = grandchild.id.value.toString()
            assertEquals(false, store.state.value.rows().single { it.id == childId }.expanded)
            assertFalse(store.state.value.rows().any { it.id == grandchildId })

            store.dispatch(Session.SetExpanded(childId, true))

            assertEquals(true, graph.catalog.findSession(child.id)?.isExpanded)
            assertTrue(store.state.value.rows().any { it.id == grandchildId })
        }
    }

    @Test
    fun projectSessionsStoreHandlesAddedProjectsAndSessionsWithoutAnAiStudioStore() = runTest {
        driver().use { driver ->
            val graph = graph(connection(driver))
            val projectId = ProjectId.Ungrouped
            repeat(6) {
                graph.repository.createSession(projectId, "Session $it", Instant.DISTANT_PAST)
            }
            val store = ProjectsSessionsStoreImpl(graph.catalog, graph.observeProjectsSessions(), backgroundScope)
            store.dispatch(Panel.Load)
            val before = store.state.value.pages.roots.getValue(projectId)
            assertNotNull(before.nextCursor)

            val project = assertIs<ProjectCreationResult.Registered>(
                graph.sessionBootstrap.registerProject(ProjectDirectory.Local("/work/Alpha".toPath())),
            ).project
            runCurrent()
            assertTrue(store.state.value.projects.any { it.id == "project:${project.id.value}" })
            assertEquals(before, store.state.value.pages.roots.getValue(projectId))

            val created = graph.repository.createSession(projectId, "Just created")
            runCurrent()

            val after = store.state.value.pages.roots.getValue(projectId)
            assertEquals(listOf(created.id) + before.items.map { it.id }, after.items.map { it.id })
            assertEquals(before.nextCursor, after.nextCursor)

            store.dispatch(Panel.Search("Beta"))
            assertTrue(store.state.value.projects.isEmpty())
            graph.sessionBootstrap.registerProject(ProjectDirectory.Local("/work/Beta".toPath()))
            runCurrent()
            assertEquals(listOf("Beta"), store.state.value.projects.map { it.name })
        }
    }

    @Test
    fun bootstrapCreatesAndSelectsOnceAndRefreshesTheOtherPanel() = runTest {
        driver().use { driver ->
            val connection = connection(driver)
            val graph = graph(connection)
            val storage = StudioWorkspacePersistence(connection)
            storage.importEmptyWorkspace()
            val panel = createStore(graph, storage)
            val bootstrap = store(TestPreferences(), graph, storage)
            runCurrent()

            bootstrap.dispatch(AiStudioAction.CreateSessionWithoutProject)
            bootstrap.dispatch(AiStudioAction.BootstrapProject.Add("/work/bootstrap"))
            runCurrent()

            assertNull(bootstrap.screenState.value.chat)
            assertTrue(panel.screenState.value.navigation.any { it.title == "bootstrap" })
            assertNotNull(panel.projectsSessionsStore).dispatch(Panel.Search("First prompt")).getOrThrow()
            runCurrent()
            assertTrue(panel.screenState.value.navigation.none { it.kind == AiStudioScreenState.NavigationKind.Chat })

            val target = assertNotNull(bootstrap.screenState.value.sessionBootstrap).project
            val submit = AiStudioAction.SubmitSessionBootstrap(target, "First prompt")
            bootstrap.dispatch(submit)
            bootstrap.dispatch(submit)
            runCurrent()

            val selected = bootstrap.screenState.value.navigation.single { it.selected }
            assertEquals("First prompt", selected.title)
            assertEquals(selected.id, bootstrap.screenState.value.chat?.id)
            assertTrue(bootstrap.screenState.value.messages.isEmpty())
            assertNull(bootstrap.screenState.value.sessionBootstrap)
            val change = assertIs<ProjectsSessionsChange.SessionAdded>(
                graph.observeProjectsSessions().replayCache.single(),
            )
            assertEquals(selected.id, change.session.id.value.toString())
            assertEquals(selected.id, panel.screenState.value.navigation.single { it.title == "First prompt" }.id)
            val saved = storage.restore { error("Workspace was already imported") }
            assertEquals("First prompt", storage.load(saved.chats.single { it.summary.id == selected.id }).draft)
        }
    }

    @Test
    fun bootstrapKeepsTheFolderWhenTheProjectNameConflicts() = runTest {
        driver().use { driver ->
            val connection = connection(driver)
            val graph = graph(connection)
            val storage = StudioWorkspacePersistence(connection)
            storage.importEmptyWorkspace()
            graph.sessionBootstrap.registerProject(ProjectDirectory.Local("/original/Alpha".toPath()))
            val store = store(TestPreferences(), graph, storage)
            val directory = "/work/Alpha"
            store.dispatch(AiStudioAction.CreateSessionWithoutProject)
            store.dispatch(AiStudioAction.BootstrapProject.Add(directory))
            runCurrent()

            val conflict = assertNotNull(store.screenState.value.sessionBootstrap)
            assertEquals(SessionBootstrapState.PendingProject(directory, "Alpha"), conflict.pendingProject)
            assertEquals(SessionBootstrapState.Error.ProjectNameTaken("Alpha"), conflict.error)
            assertEquals(
                listOf("Alpha", "Без проекта"),
                graph.catalog.listProjects(ProjectStatus.ACTIVE).items.map { it.title },
            )

            store.dispatch(AiStudioAction.BootstrapProject.Retry("New Alpha"))
            runCurrent()

            val created = assertIs<Project.WithDirectory>(
                graph.catalog.listProjects(ProjectStatus.ACTIVE).items.single { it.title == "New Alpha" },
            )
            assertEquals(directory.toPath(), created.workingDirectory)
            val ready = assertNotNull(store.screenState.value.sessionBootstrap)
            assertEquals(SessionBootstrapState.Project.Existing(created.id.value.toString()), ready.project)
            assertNull(ready.pendingProject)
            assertNull(store.screenState.value.chat)
        }
    }
}

private suspend fun seedPagedProjects(graph: StudioInteractorTestGraph): StoredSession {
    repeat(40) { graph.catalog.createProject("/project-$it".toPath(), name = "project $it") }
    val project = assertIs<ProjectCreationResult.Registered>(
        graph.catalog.createProject("/paged".toPath(), name = "Paged project"),
    ).project
    val root = graph.repository.createSession(project.id, "Parent", Instant.parse("2026-11-21T17:00:00Z"))
    val time = Instant.parse("2026-11-21T16:00:00Z")
    repeat(5) { graph.repository.createSession(project.id, "root $it", time) }
    repeat(6) { graph.repository.createSession(project.id, "child $it", time, root.id) }
    return root
}

private fun TestScope.openChat(store: AiStudioStore, id: String) {
    store.dispatch(AiStudioAction.OpenChat(id))
    runCurrent()
}

private fun TestScope.createStore(graph: StudioInteractorTestGraph, storage: StudioWorkspacePersistence) =
    DefaultAiStudioStoreFactory(
        object : PreferencesStoreFactory {
            override fun forNamespace(namespace: String) = TestPreferences()
        },
        graph.catalog,
        graph.observeProjectsSessions,
        graph.sessionBootstrap,
        storage,
    ).create(backgroundScope)

private fun TestScope.store(
    preferences: TestPreferences,
    graph: StudioInteractorTestGraph,
    storage: StudioWorkspacePersistence,
): DefaultAiStudioStore =
    DefaultAiStudioStore(
        preferences,
        backgroundScope,
        graph.catalog,
        storage,
        graph.observeProjectsSessions(),
        graph.sessionBootstrap,
    )

private fun driver() = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
    HeartbeatDatabase.Schema.create(it)
}

private fun connection(driver: SqlDriver) = object : DatabaseConnection {
    override val driver = driver
}

private fun graph(connection: DatabaseConnection) =
    createGraphFactory<StudioInteractorTestGraph.Factory>().create(connection)

private suspend fun StudioWorkspacePersistence.importEmptyWorkspace() {
    restore { AuroraWorkspace("", listOf()) }
}

private fun legacyWorkspace(project: String = "Imported project") = AuroraWorkspace(
    selectedId = "saved",
    chats = listOf(
        AuroraChat(
            summary = AiStudioScreenState.Chat("saved", "Imported session", project),
            draft = "saved draft",
            anchor = AiStudioScreenState.Anchor("message", 3),
            messages = listOf(AiStudioScreenState.User("message", "12:00", "saved message")),
        ),
    ),
    collapsedNavigation = setOf("project:$project"),
)
