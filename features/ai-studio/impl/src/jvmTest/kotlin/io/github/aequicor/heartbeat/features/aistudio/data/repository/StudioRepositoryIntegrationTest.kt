package io.github.aequicor.heartbeat.features.aistudio.data.repository

import app.cash.sqldelight.SuspendingTransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.zacsweers.metro.createGraphFactory
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.features.aistudio.data.dao.SqlDelightProjectDao
import io.github.aequicor.heartbeat.features.aistudio.data.dao.SqlDelightStudioSessionDao
import io.github.aequicor.heartbeat.features.aistudio.data.exception.DaoException
import io.github.aequicor.heartbeat.features.aistudio.data.exception.ProjectNameAlreadyExistsException
import io.github.aequicor.heartbeat.features.aistudio.data.model.DatabaseTimestamp
import io.github.aequicor.heartbeat.features.aistudio.db.HeartbeatDatabase
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.ProjectInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.interactor.StudioSessionInteractor
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectCreationResult
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionStatus
import io.github.aequicor.heartbeat.features.aistudio.domain.model.StudioSessionId
import io.github.aequicor.heartbeat.features.aistudio.domain.repository.ProjectRepository
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
    fun changingExpansionOnlyUpdatesTheAddressedNode() = runTest {
        driver().use { driver ->
            val graph = createGraphFactory<StudioInteractorTestGraph.Factory>().create(connection(driver))
            val project = assertIs<ProjectCreationResult.Created>(
                graph.projects.create("Alpha", "/alpha".toPath()),
            ).project
            val root = graph.sessions.start(project.id, "Root", Start)
            val child = graph.sessions.start(project.id, "Child", Later, root.id)
            val sibling = graph.sessions.start(project.id, "Sibling", Latest)
            assertEquals(true, project.isExpanded)
            assertEquals(true, root.isExpanded)
            assertEquals(true, child.isExpanded)

            graph.sessions.setExpanded(child.id, false)
            graph.projects.setExpanded(project.id, false)
            graph.sessions.setExpanded(root.id, false)
            assertEquals(project.copy(isExpanded = false), graph.projects.list(ProjectStatus.ACTIVE, 1).items.single())
            assertEquals(root.copy(isExpanded = false), graph.sessions.findById(root.id))
            assertEquals(
                listOf(sibling, root.copy(isExpanded = false)),
                graph.sessions.listRoots(project.id, SessionStatus.ACTIVE, 2).items,
            )
            assertEquals(
                listOf(child.copy(isExpanded = false)),
                graph.sessions.listChildren(root.id, SessionStatus.ACTIVE, 1).items,
            )
            graph.projects.setExpanded(project.id, true)
            assertEquals(project, graph.projects.findById(project.id))
            assertEquals(root.copy(isExpanded = false), graph.sessions.findById(root.id))
        }
    }

    @Test
    fun projectTitlesCanRepeatWhileImmutableNamesStayUnique() = runTest {
        driver().use { driver ->
            var time = Start
            val clock = object : Clock {
                override fun now() = time
            }
            val dao = SqlDelightProjectDao(connection(driver), clock)
            val projects = ProjectInteractor(DefaultProjectRepository(dao))
            val alpha = assertIs<ProjectCreationResult.Created>(projects.create("Alpha", "/alpha".toPath())).project
            val beta = assertIs<ProjectCreationResult.Created>(projects.create("Beta", "/beta".toPath())).project
            val row = assertNotNull(dao.findById(alpha.id.value.toString()))
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
            val first = projects.list(ProjectStatus.ACTIVE, 1)
            val second = projects.list(ProjectStatus.ACTIVE, 1, assertNotNull(first.nextCursor))
            assertEquals(expected, first.items + second.items)
            assertNull(second.nextCursor)

            time = Later
            projects.renameTitle(alpha.id, beta.title)
            val renamed = assertNotNull(projects.findById(alpha.id))
            assertEquals(alpha.copy(title = beta.title, updatedAt = Later), renamed)
            assertEquals(listOf(renamed, beta), projects.list(ProjectStatus.ACTIVE, 2).items)

            time = Latest
            projects.setStatus(alpha.id, ProjectStatus.ARCHIVED)
            assertEquals(
                listOf(renamed.copy(status = ProjectStatus.ARCHIVED, updatedAt = Latest)),
                projects.list(ProjectStatus.ARCHIVED, 2).items,
            )
            val conflict = assertFailsWith<ProjectNameAlreadyExistsException> { dao.create("Alpha", "/elsewhere") }
            assertEquals("Alpha", conflict.immutableName)
            assertEquals(
                ProjectCreationResult.NameAlreadyExists("Alpha"),
                projects.create("Alpha", "/elsewhere".toPath()),
            )
            assertEquals(listOf(beta), projects.list(ProjectStatus.ACTIVE, 2).items)
        }
    }

    @Test
    fun sessionsPageByProjectParentStatusAndExactActivity() = runTest {
        driver().use { driver ->
            val connection = connection(driver)
            val projects = ProjectInteractor(DefaultProjectRepository(SqlDelightProjectDao(connection)))
            val alpha = assertIs<ProjectCreationResult.Created>(projects.create("Alpha", "/alpha".toPath())).project
            val beta = assertIs<ProjectCreationResult.Created>(projects.create("Beta", "/beta".toPath())).project
            val dao = SqlDelightStudioSessionDao(connection)
            val sessions = StudioSessionInteractor(DefaultStudioSessionRepository(dao))
            val root = sessions.start(alpha.id, "Title provided by caller", Start)
            val sibling = sessions.start(alpha.id, "Sibling", Start)
            val child = sessions.start(alpha.id, "Child", Later, root.id)
            val anotherChild = sessions.start(alpha.id, "Second child", Later, root.id)
            val grandchild = sessions.start(alpha.id, "Grandchild", Start, child.id)
            sessions.start(beta.id, "Other project", Later)
            assertFailsWith<StudioRepositoryException> { sessions.start(beta.id, "Wrong project", Start, root.id) }
            assertFailsWith<StudioRepositoryException> {
                sessions.start(alpha.id, "Missing parent", Start, StudioSessionId(Uuid.random()))
            }
            assertEquals("Title provided by caller", root.title)
            assertEquals(Start, root.createdAt)
            assertEquals(Start, root.updatedAt)
            assertEquals(SessionStatus.ACTIVE, root.status)
            assertEquals(root.id, child.parentSessionId)
            assertEquals(alpha.id, child.projectId)

            val roots = sessions.listRoots(alpha.id, SessionStatus.ACTIVE, 1)
            val moreRoots = sessions.listRoots(alpha.id, SessionStatus.ACTIVE, 1, assertNotNull(roots.nextCursor))
            assertEquals(listOf(root, sibling).sortedBy { it.id.value.toString() }, roots.items + moreRoots.items)
            assertNull(moreRoots.nextCursor)
            val children = sessions.listChildren(root.id, SessionStatus.ACTIVE, 1)
            val moreChildren = sessions.listChildren(
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
            assertEquals(listOf(grandchild), sessions.listChildren(child.id, SessionStatus.ACTIVE, 10).items)

            val siblingId = sibling.id.value.toString()
            dao.recordActivity(siblingId, Later.toDatabaseTimestamp())
            dao.recordActivity(siblingId, Start.toDatabaseTimestamp())
            sessions.rename(sibling.id, "Renamed")
            sessions.setStatus(sibling.id, SessionStatus.ACTIVE, Latest)
            assertEquals(sibling.copy(title = "Renamed", updatedAt = Later), sessions.findById(sibling.id))
            val newest = sessions.listRoots(alpha.id, SessionStatus.ACTIVE, 1)
            assertEquals(sibling.id, newest.items.single().id)
            val older = sessions.listRoots(alpha.id, SessionStatus.ACTIVE, 1, assertNotNull(newest.nextCursor))
            assertEquals(root, older.items.single())
            sessions.setStatus(sibling.id, SessionStatus.ARCHIVED, Start)
            assertEquals(Later, sessions.listRoots(alpha.id, SessionStatus.ARCHIVED, 1).items.single().updatedAt)
            sessions.setStatus(sibling.id, SessionStatus.ACTIVE, Latest)
            assertEquals(Latest, sessions.findById(sibling.id)?.updatedAt)
        }
    }

    @Test
    fun injectedInteractorStartsSessionInsideTheCallersTransaction() = runTest {
        driver().use { driver ->
            val connection = connection(driver)
            val graph = createGraphFactory<StudioInteractorTestGraph.Factory>().create(connection)
            val project = assertIs<ProjectCreationResult.Created>(
                graph.projects.create("Alpha", "/alpha".toPath()),
            ).project
            val sessions = graph.sessions
            val transaction = object : SuspendingTransacterImpl(driver) {}
            driver.execute(null, "CREATE TABLE test_first_message (session_id TEXT NOT NULL)", 0)
            var rolledBack: StudioSessionId? = null

            assertFailsWith<IllegalStateException> {
                transaction.transaction {
                    val session = sessions.start(project.id, "First", Start)
                    rolledBack = session.id
                    writeFirstMessage(driver, session.id)
                    error("Failure after writing the first message")
                }
            }
            assertNull(sessions.findById(assertNotNull(rolledBack)))
            assertEquals(0L, messageCount(driver))

            val committed = transaction.transactionWithResult {
                val session = sessions.start(project.id, "First", Start)
                writeFirstMessage(driver, session.id)
                session
            }
            assertEquals(committed, sessions.findById(committed.id))
            assertEquals(1L, messageCount(driver))
        }
    }

    @Test
    fun wrapsStorageFailuresAndPropagatesCancellation() = runTest {
        val failure = IllegalStateException("Database unavailable")
        val project = SqlDelightProjectDao(unavailable(failure))
        assertSame(failure, assertFailsWith<DaoException> { project.findById(Uuid.random().toString()) }.cause)
        val repository: ProjectRepository = DefaultProjectRepository(project)
        assertSame(
            failure,
            assertFailsWith<StudioRepositoryException> {
                repository.findById(ProjectId(Uuid.random()))
            }.cause,
        )
        val cancelled = CancellationException("Cancelled")
        val sessions = SqlDelightStudioSessionDao(unavailable(cancelled))
        assertSame(
            cancelled,
            assertFailsWith<CancellationException> {
                sessions.findById(Uuid.random().toString())
            },
        )
    }

    private fun driver() = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
        HeartbeatDatabase.Schema.create(it)
    }

    private fun connection(driver: SqlDriver) = object : DatabaseConnection {
        override val driver = driver
    }

    private fun unavailable(failure: Exception) = object : DatabaseConnection {
        override val driver: SqlDriver get() = throw failure
    }

    private fun writeFirstMessage(driver: SqlDriver, session: StudioSessionId) {
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
