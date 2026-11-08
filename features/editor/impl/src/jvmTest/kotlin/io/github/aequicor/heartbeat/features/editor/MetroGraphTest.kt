package io.github.aequicor.heartbeat.features.editor

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.editor.api.EditorAction
import io.github.aequicor.heartbeat.features.editor.api.EditorState
import io.github.aequicor.heartbeat.features.editor.api.EditorStoreFactory
import io.github.aequicor.heartbeat.features.editor.db.HeartbeatDatabase
import io.github.aequicor.heartbeat.features.editor.domain.repository.EditorRepository
import java.util.Properties
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Proves that Metro contributions from `commonMain` reach the final graph and that the editor
 * generated schema runs on a database created from its own SQL set. The graph lives in the test
 * compilation, so a missing or conflicting binding fails the build.
 */
@DependencyGraph(AppScope::class)
internal interface EditorTestGraph {

    val editorStoreFactory: EditorStoreFactory

    val repository: EditorRepository

    @DependencyGraph.Factory
    fun interface Factory {

        fun create(@Provides connection: DatabaseConnection): EditorTestGraph
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MetroGraphTest {

    @Test
    fun contributionsReachTheGraph() = runTest {
        val graph = createGraph()

        assertNotNull(graph.repository)

        val store = graph.editorStoreFactory.create("n1", this)
        advanceUntilIdle()

        assertEquals(EditorState(title = "", content = "", isLoading = false), store.state.value)
        assertFalse(store.state.value.isLoading)
    }

    @Test
    fun savedNoteIsVisibleToNextStore() = runTest {
        val graph = createGraph()
        val first = graph.editorStoreFactory.create("n1", this)
        advanceUntilIdle()

        first.dispatch(EditorAction.TitleChanged("Сохранено"))
        first.dispatch(EditorAction.Save)
        advanceUntilIdle()

        val second = graph.editorStoreFactory.create("n1", this)
        advanceUntilIdle()

        assertEquals("Сохранено", second.state.value.title)
    }

    private fun createGraph(): EditorTestGraph {
        val driver = JdbcSqliteDriver(
            JdbcSqliteDriver.IN_MEMORY,
            Properties(),
            HeartbeatDatabase.Schema,
        )
        val connection = object : DatabaseConnection {
            override val driver: SqlDriver = driver
        }
        return createGraphFactory<EditorTestGraph.Factory>().create(connection)
    }
}
