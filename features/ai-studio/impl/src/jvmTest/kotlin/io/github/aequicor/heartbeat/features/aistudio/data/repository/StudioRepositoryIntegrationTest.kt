package io.github.aequicor.heartbeat.features.aistudio.data.repository

import app.cash.sqldelight.SuspendingTransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.zacsweers.metro.createGraphFactory
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.features.aistudio.data.dao.SqlDelightProjectDao
import io.github.aequicor.heartbeat.features.aistudio.data.dao.SqlDelightSessionDao
import io.github.aequicor.heartbeat.features.aistudio.data.datasource.DefaultProjectDataSource
import io.github.aequicor.heartbeat.features.aistudio.data.datasource.DefaultSessionsDataSource
import io.github.aequicor.heartbeat.features.aistudio.data.datasource.SqlDelightProjectsSessionsSearchDataSource
import io.github.aequicor.heartbeat.features.aistudio.data.datasource.toDatabaseTimestamp
import io.github.aequicor.heartbeat.features.aistudio.data.exception.DaoException
import io.github.aequicor.heartbeat.features.aistudio.data.exception.ProjectNameAlreadyExistsException
import io.github.aequicor.heartbeat.features.aistudio.data.model.DatabaseTimestamp
import io.github.aequicor.heartbeat.features.aistudio.db.HeartbeatDatabase
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ProjectsSessionsInteractorImpl
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ProjectsSessionsInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Project
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectCreationResult
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectDirectory
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectsSessionsChange
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionBootstrapResult
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionId
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ProjectDataSource
import io.github.aequicor.heartbeat.features.aistudio.presentation.DefaultAiStudioStore
import io.github.aequicor.heartbeat.features.aistudio.presentation.TestPreferences
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.DeletionError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class StudioRepositoryIntegrationTest {

    @Test
    fun bootstrapPersistsTheDirectoryAndRejectsADuplicateName() = runTest {
        driver().use { driver ->
            val connection = connection(driver)
            val graph = createGraphFactory<StudioInteractorTestGraph.Factory>().create(connection)
            val path = "/work/first-directory".toPath()
            val directory = ProjectDirectory.Local(path)
            val created = assertIs<ProjectCreationResult.Registered>(
                graph.sessionBootstrap.registerProject(directory, "Alpha"),
            )

            val project = created.project
            assertEquals("Alpha", project.immutableName)
            assertEquals(path, project.workingDirectory)
            assertEquals(listOf(), graph.catalog.listRootSessions(project.id, SessionStatus.ACTIVE).items)

            val duplicate = graph.sessionBootstrap.registerProject(ProjectDirectory.Local("/other/Alpha".toPath()))
            assertEquals(ProjectCreationResult.NameConflict("Alpha"), duplicate)
            assertEquals(
                listOf(project, Project.WithoutDirectory(isExpanded = true)),
                graph.catalog.listProjects(ProjectStatus.ACTIVE, 2).items,
            )
            assertEquals(
                ProjectsSessionsChange.ProjectAdded(created.project.id),
                graph.observeProjectsSessions().replayCache.single(),
            )

            val next = assertIs<SessionBootstrapResult.Created>(
                graph.sessionBootstrap.createSession(project.id, "Continue"),
            )
            assertEquals(project, next.project)
            assertEquals(project.id, next.session.projectId)
            assertNull(
                HeartbeatDatabase(driver).studioWorkspaceQueries
                    .selectWorkspacePayload(next.session.id.value.toString()).executeAsOneOrNull()?.workspace_payload,
            )
            assertEquals(
                listOf(project, Project.WithoutDirectory(isExpanded = true)),
                graph.catalog.listProjects(ProjectStatus.ACTIVE, 2).items,
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun deleteSessionActionRemovesOnlyTheSelectedSubtree() = runTest {
        driver().use { driver ->
            val graph = createGraphFactory<StudioInteractorTestGraph.Factory>().create(connection(driver))
            val store = DefaultAiStudioStore(TestPreferences(), backgroundScope, graph.catalog)
            val project = assertIs<ProjectCreationResult.Registered>(
                graph.catalog.createProject("/alpha".toPath(), name = "Alpha"),
            ).project
            val root = graph.repository.createSession(project.id, "Root", Start)
            val child = graph.repository.createSession(project.id, "Child", Later, root.id)
            val grandchild = graph.repository.createSession(project.id, "Grandchild", Latest, child.id)
            val sibling = graph.repository.createSession(project.id, "Sibling", Latest, root.id)

            store.dispatch(AiStudioAction.DeleteSession(child.id.value.toString()))
            store.dispatch(AiStudioAction.DeleteSession(child.id.value.toString()))
            runCurrent()

            assertNull(store.screenState.value.deletionError)
            assertNull(graph.catalog.findSession(child.id))
            assertNull(graph.catalog.findSession(grandchild.id))
            assertEquals(root, graph.catalog.findSession(root.id))
            assertEquals(listOf(sibling), graph.catalog.listChildSessions(root.id, SessionStatus.ACTIVE, 2).items)
            assertEquals(project, graph.projectDataSource.findById(project.id))
        }
    }

    @Test
    fun bootstrapStartsSessionsInTheRegisteredSystemProject() = runTest {
        driver().use { driver ->
            val graph = createGraphFactory<StudioInteractorTestGraph.Factory>().create(connection(driver))
            val project = assertIs<Project.WithoutDirectory>(
                graph.catalog.listProjects(ProjectStatus.ACTIVE).items.single(),
            )
            assertEquals(Project.WithoutDirectory(isExpanded = true), project)
            assertEquals(project, graph.projectDataSource.findById(ProjectId.Ungrouped))
            assertEquals(ProjectId.Ungrouped, project.id)
            assertEquals("Без проекта", project.title)
            val first = assertIs<SessionBootstrapResult.Created>(
                graph.sessionBootstrap.createSession(project.id, "First"),
            )
            assertFailsWith<IllegalArgumentException> { graph.catalog.deleteProject(project) }
            assertFailsWith<IllegalArgumentException> { graph.catalog.renameProject(project, "Renamed") }
            assertFailsWith<IllegalArgumentException> { graph.catalog.setProjectArchived(project, isArchived = true) }
            val second = assertIs<SessionBootstrapResult.Created>(
                graph.sessionBootstrap.createSession(project.id, "Second"),
            )
            assertEquals(project.id, graph.catalog.findSession(first.session.id)?.projectId)
            assertEquals(project.id, graph.catalog.findSession(second.session.id)?.projectId)
            graph.catalog.setProjectExpanded(project, false)
            val collapsed = project.copy(isExpanded = false)
            assertEquals(collapsed, graph.projectDataSource.findById(project.id))
            assertEquals(listOf(collapsed), graph.catalog.listProjects(ProjectStatus.ACTIVE).items)
            assertEquals(
                ProjectsSessionsChange.SessionAdded(second.session, second.project),
                graph.observeProjectsSessions().replayCache.single(),
            )
        }
    }

    @Test
    fun sessionFailureKeepsThePreviouslyRegisteredProject() = runTest {
        driver().use { driver ->
            val graph = createGraphFactory<StudioInteractorTestGraph.Factory>().create(connection(driver))
            val project = assertIs<ProjectCreationResult.Registered>(
                graph.sessionBootstrap.registerProject(ProjectDirectory.Local("/work/Alpha".toPath())),
            ).project
            driver.execute(
                null,
                """
                CREATE TRIGGER reject_session BEFORE INSERT ON studio_session
                BEGIN SELECT RAISE(ABORT, 'test rejection'); END
                """.trimIndent(),
                0,
            )
            assertFailsWith<StudioRepositoryException> {
                graph.sessionBootstrap.createSession(project.id, "Start")
            }
            assertEquals(project, graph.projectDataSource.findById(project.id))
            assertEquals(
                ProjectsSessionsChange.ProjectAdded(project.id),
                graph.observeProjectsSessions().replayCache.single(),
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun deleteProjectActionRollsBackSessionsWhenProjectDeletionFails() = runTest {
        driver().use { driver ->
            driver.execute(null, "PRAGMA foreign_keys = ON", 0)
            val graph = createGraphFactory<StudioInteractorTestGraph.Factory>().create(connection(driver))
            val store = DefaultAiStudioStore(TestPreferences(), backgroundScope, graph.catalog)
            val alpha = assertIs<ProjectCreationResult.Registered>(
                graph.catalog.createProject("/alpha".toPath(), name = "Alpha"),
            ).project
            val beta = assertIs<ProjectCreationResult.Registered>(
                graph.catalog.createProject("/beta".toPath(), name = "Beta"),
            ).project
            val root = graph.repository.createSession(alpha.id, "Root", Start)
            val child = graph.repository.createSession(alpha.id, "Child", Later, root.id)
            graph.catalog.setSessionArchived(child, isArchived = true, changedAt = Latest)
            val unrelated = graph.repository.createSession(beta.id, "Unrelated", Start)
            driver.execute(
                null,
                """
                CREATE TRIGGER reject_project_delete BEFORE DELETE ON studio_project
                BEGIN SELECT RAISE(ABORT, 'test rejection'); END
                """.trimIndent(),
                0,
            )

            val delete = AiStudioAction.DeleteProject(alpha.id.value.toString())
            store.dispatch(delete)
            runCurrent()
            assertEquals(DeletionError.Project, store.screenState.value.deletionError)
            assertEquals(alpha, graph.projectDataSource.findById(alpha.id))
            assertEquals(root, graph.catalog.findSession(root.id))
            assertEquals(SessionStatus.ARCHIVED, graph.catalog.findSession(child.id)?.status)

            driver.execute(null, "DROP TRIGGER reject_project_delete", 0)
            store.dispatch(delete)
            runCurrent()
            assertNull(store.screenState.value.deletionError)
            assertNull(graph.projectDataSource.findById(alpha.id))
            assertNull(graph.catalog.findSession(root.id))
            assertNull(graph.catalog.findSession(child.id))
            assertEquals(beta, graph.projectDataSource.findById(beta.id))
            assertEquals(unrelated, graph.catalog.findSession(unrelated.id))
        }
    }

    @Test
    fun changingExpansionOnlyUpdatesTheAddressedNode() = runTest {
        driver().use { driver ->
            val graph = createGraphFactory<StudioInteractorTestGraph.Factory>().create(connection(driver))
            val project = assertIs<ProjectCreationResult.Registered>(
                graph.catalog.createProject("/alpha".toPath(), name = "Alpha"),
            ).project
            val root = graph.repository.createSession(project.id, "Root", Start)
            val child = graph.repository.createSession(project.id, "Child", Later, root.id)
            val sibling = graph.repository.createSession(project.id, "Sibling", Latest)
            assertEquals(true, project.isExpanded)
            assertEquals(true, root.isExpanded)
            assertEquals(true, child.isExpanded)

            graph.catalog.setSessionExpanded(child, false)
            graph.catalog.setProjectExpanded(project, false)
            graph.catalog.setSessionExpanded(root, false)
            val collapsed = project.copy(isExpanded = false)
            assertEquals(collapsed, graph.projectDataSource.findById(project.id))
            assertEquals(collapsed, graph.catalog.listProjects(ProjectStatus.ACTIVE, 1).items.single())
            assertEquals(root.copy(isExpanded = false), graph.catalog.findSession(root.id))
            assertEquals(
                listOf(sibling, root.copy(isExpanded = false)),
                graph.catalog.listRootSessions(project.id, SessionStatus.ACTIVE, 2).items,
            )
            assertEquals(
                listOf(child.copy(isExpanded = false)),
                graph.catalog.listChildSessions(root.id, SessionStatus.ACTIVE, 1).items,
            )
            graph.catalog.setProjectExpanded(project, true)
            assertEquals(project, graph.catalog.listProjects(ProjectStatus.ACTIVE, 1).items.single())
            assertEquals(root.copy(isExpanded = false), graph.catalog.findSession(root.id))
        }
    }

    @Test
    fun projectTitlesCanRepeatWhileImmutableNamesStayUnique() = runTest {
        driver().use { driver ->
            var time = Start
            val clock = object : Clock {
                override fun now() = time
            }
            val connection = connection(driver)
            val dao = SqlDelightProjectDao(connection, clock)
            val projects = DefaultProjectDataSource(dao)
            val catalog = ProjectsSessionsInteractorImpl(
                projects,
                DefaultSessionsDataSource(SqlDelightSessionDao(connection)),
                SqlDelightProjectsSessionsSearchDataSource(connection),
            )
            val alpha = catalog.registerProjectFixture("/alpha", "Alpha")
            val beta = catalog.registerProjectFixture("/beta", "Beta")
            val row = assertNotNull(dao.selectById(alpha.id.value.toString()))
            assertEquals(alpha.id.value.toString(), row.id)
            assertEquals("ACTIVE", row.status)
            assertEquals(DatabaseTimestamp(Start.epochSeconds, Start.nanosecondsOfSecond.toLong()), row.createdAt)
            assertEquals("/alpha", row.workingDirectory)
            assertEquals("/alpha".toPath(), alpha.workingDirectory)
            assertEquals(alpha.immutableName, alpha.title)
            assertEquals(Start, alpha.createdAt)
            assertEquals(Start, alpha.updatedAt)
            assertEquals(ProjectStatus.ACTIVE, alpha.status)
            assertNull(projects.findById(ProjectId(Uuid.random())))

            val expected = listOf(alpha, beta).sortedBy { it.id.value.toString() }
            val first = catalog.listProjects(ProjectStatus.ACTIVE, 1)
            val second = catalog.listProjects(ProjectStatus.ACTIVE, 1, assertNotNull(first.nextCursor))
            assertEquals(expected, first.items + second.items)
            val third = catalog.listProjects(ProjectStatus.ACTIVE, 1, assertNotNull(second.nextCursor))
            assertEquals(Project.WithoutDirectory(isExpanded = true), third.items.single())
            assertNull(third.nextCursor)

            time = Later
            catalog.renameProject(alpha, beta.title)
            val renamed = assertIs<Project.WithDirectory>(projects.findById(alpha.id))
            assertEquals(alpha.copy(title = beta.title, updatedAt = Later), renamed)
            assertEquals(listOf(renamed, beta), catalog.listProjects(ProjectStatus.ACTIVE, 2).items)

            time = Latest
            catalog.setProjectArchived(alpha, isArchived = true)
            assertEquals(
                listOf(renamed.copy(status = ProjectStatus.ARCHIVED, updatedAt = Latest)),
                catalog.listProjects(ProjectStatus.ARCHIVED, 2).items,
            )
            val conflict = assertFailsWith<ProjectNameAlreadyExistsException> { dao.insert("Alpha", "/elsewhere") }
            assertEquals("Alpha", conflict.immutableName)
            assertEquals(
                ProjectCreationResult.NameConflict("Alpha"),
                catalog.createProject("/elsewhere".toPath(), name = "Alpha"),
            )
            assertEquals(
                listOf(beta.id, ProjectId.Ungrouped),
                catalog.listProjects(ProjectStatus.ACTIVE, 2).items.map { it.id },
            )
            catalog.setProjectArchived(alpha, isArchived = false)
            assertEquals(ProjectStatus.ACTIVE, projects.findById(alpha.id)?.status)
        }
    }

    @Test
    fun sessionsPageByProjectParentStatusAndExactActivity() = runTest {
        driver().use { driver ->
            val connection = connection(driver)
            val graph = createGraphFactory<StudioInteractorTestGraph.Factory>().create(connection)
            val catalog = graph.catalog
            val dao = SqlDelightSessionDao(connection)
            val alpha = catalog.registerProjectFixture("/alpha", "Alpha")
            val beta = catalog.registerProjectFixture("/beta", "Beta")
            val root = graph.repository.createSession(alpha.id, "Title provided by caller", Start)
            val sibling = graph.repository.createSession(alpha.id, "Sibling", Start)
            val child = graph.repository.createSession(alpha.id, "Child", Later, root.id)
            val anotherChild = graph.repository.createSession(alpha.id, "Second child", Later, root.id)
            val grandchild = graph.repository.createSession(alpha.id, "Grandchild", Start, child.id)
            graph.repository.createSession(beta.id, "Other project", Later)
            assertFailsWith<StudioRepositoryException> {
                graph.repository.createSession(beta.id, "Wrong project", Start, root.id)
            }
            assertFailsWith<StudioRepositoryException> {
                graph.repository.createSession(alpha.id, "Missing parent", Start, SessionId(Uuid.random()))
            }
            assertEquals("Title provided by caller", root.title)
            assertEquals(Start, root.createdAt)
            assertEquals(Start, root.updatedAt)
            assertEquals(SessionStatus.ACTIVE, root.status)
            assertEquals(root.id, child.parentSessionId)
            assertEquals(alpha.id, child.projectId)

            val roots = catalog.listRootSessions(alpha.id, SessionStatus.ACTIVE, 1)
            val moreRoots = catalog.listRootSessions(alpha.id, SessionStatus.ACTIVE, 1, assertNotNull(roots.nextCursor))
            assertEquals(listOf(root, sibling).sortedBy { it.id.value.toString() }, roots.items + moreRoots.items)
            assertNull(moreRoots.nextCursor)
            val children = catalog.listChildSessions(root.id, SessionStatus.ACTIVE, 1)
            val moreChildren = catalog.listChildSessions(
                root.id,
                SessionStatus.ACTIVE,
                1,
                assertNotNull(children.nextCursor),
            )
            assertEquals(
                listOf(child, anotherChild).sortedBy { it.id.value.toString() },
                children.items + moreChildren.items,
            )
            assertNull(moreChildren.nextCursor)
            assertEquals(listOf(grandchild), catalog.listChildSessions(child.id, SessionStatus.ACTIVE, 10).items)

            val siblingId = sibling.id.value.toString()
            dao.updateActivity(siblingId, Later.toDatabaseTimestamp())
            dao.updateActivity(siblingId, Start.toDatabaseTimestamp())
            catalog.renameSession(sibling, "Renamed")
            catalog.setSessionArchived(sibling, isArchived = false, changedAt = Latest)
            assertEquals(sibling.copy(title = "Renamed", updatedAt = Later), catalog.findSession(sibling.id))
            val newest = catalog.listRootSessions(alpha.id, SessionStatus.ACTIVE, 1)
            assertEquals(sibling.id, newest.items.single().id)
            val older = catalog.listRootSessions(alpha.id, SessionStatus.ACTIVE, 1, assertNotNull(newest.nextCursor))
            assertEquals(root, older.items.single())
            catalog.setSessionArchived(sibling, isArchived = true, changedAt = Start)
            assertEquals(Later, catalog.listRootSessions(alpha.id, SessionStatus.ARCHIVED, 1).items.single().updatedAt)
            catalog.setSessionArchived(sibling, isArchived = false, changedAt = Latest)
            assertEquals(Latest, catalog.findSession(sibling.id)?.updatedAt)
        }
    }

    @Test
    fun bootstrapCreatesSessionInsideTheCallersTransaction() = runTest {
        driver().use { driver ->
            val connection = connection(driver)
            val graph = createGraphFactory<StudioInteractorTestGraph.Factory>().create(connection)
            val project = assertIs<ProjectCreationResult.Registered>(
                graph.catalog.createProject("/alpha".toPath(), name = "Alpha"),
            ).project
            val catalog = graph.catalog
            val bootstrap = graph.sessionBootstrap
            val transaction = object : SuspendingTransacterImpl(driver) {}
            driver.execute(null, "CREATE TABLE test_first_message (session_id TEXT NOT NULL)", 0)
            var rolledBack: SessionId? = null

            assertFailsWith<IllegalStateException> {
                transaction.transaction {
                    val session = assertIs<SessionBootstrapResult.Created>(
                        bootstrap.createSession(project.id, "First", Start),
                    ).session
                    rolledBack = session.id
                    writeFirstMessage(driver, session.id)
                    error("Failure after writing the first message")
                }
            }
            assertNull(catalog.findSession(assertNotNull(rolledBack)))
            assertEquals(0L, messageCount(driver))

            val committed = transaction.transactionWithResult {
                val session = assertIs<SessionBootstrapResult.Created>(
                    bootstrap.createSession(project.id, "First", Start),
                ).session
                writeFirstMessage(driver, session.id)
                session
            }
            assertEquals(committed, catalog.findSession(committed.id))
            assertEquals(1L, messageCount(driver))
        }
    }

    @Test
    fun wrapsStorageFailuresAndPropagatesCancellation() = runTest {
        val failure = IllegalStateException("Database unavailable")
        val project = SqlDelightProjectDao(unavailable(failure))
        assertSame(failure, assertFailsWith<DaoException> { project.selectById(Uuid.random().toString()) }.cause)
        val repository: ProjectDataSource = DefaultProjectDataSource(project)
        assertSame(
            failure,
            assertFailsWith<StudioRepositoryException> {
                repository.findById(ProjectId(Uuid.random()))
            }.cause,
        )
        val cancelled = CancellationException("Cancelled")
        val sessions = SqlDelightSessionDao(unavailable(cancelled))
        assertSame(
            cancelled,
            assertFailsWith<CancellationException> {
                sessions.selectById(Uuid.random().toString())
            },
        )
    }

    private fun driver() = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
        HeartbeatDatabase.Schema.create(it)
    }

    private suspend fun ProjectsSessionsInteractor.registerProjectFixture(path: String, name: String) =
        assertIs<ProjectCreationResult.Registered>(createProject(path.toPath(), name = name)).project

    private fun connection(driver: SqlDriver) = object : DatabaseConnection {
        override val driver = driver
    }

    private fun unavailable(failure: Exception) = object : DatabaseConnection {
        override val driver: SqlDriver get() = throw failure
    }

    private fun writeFirstMessage(driver: SqlDriver, session: SessionId) {
        driver.execute(null, "INSERT INTO test_first_message VALUES (?)", 1) {
            bindString(0, session.value.toString())
        }
    }

    private fun messageCount(driver: SqlDriver): Long = driver.executeQuery(
        null,
        "SELECT count(*) FROM test_first_message",
        { cursor ->
            check(cursor.next().value)
            QueryResult.Value(checkNotNull(cursor.getLong(0)))
        },
        0,
    ).value

    private companion object {
        val Start = Instant.parse("2026-11-21T12:00:00.000000001Z")
        val Later = Instant.parse("2026-11-21T12:00:00.000000002Z")
        val Latest = Instant.parse("2026-11-21T12:00:01Z")
    }
}
