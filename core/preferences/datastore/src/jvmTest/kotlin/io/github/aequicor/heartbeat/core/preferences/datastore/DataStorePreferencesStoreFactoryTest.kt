package io.github.aequicor.heartbeat.core.preferences.datastore

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking

/**
 * DataStore implementation checks: typed defaults, namespace isolation and persistence across
 * factory instances that open the same physical file one after another.
 */
@Suppress("InjectDispatcher") // Real IO threads are the point of the persistence test.
class DataStorePreferencesStoreFactoryTest {

    @Test
    fun namespacesDoNotSeeEachOther() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("heartbeat-preferences").toString()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val factory = DataStorePreferencesStoreFactory(directory, scope)
            val aiStudio = factory.forNamespace("ai-studio")
            val editor = factory.forNamespace("editor")

            aiStudio.putString("engine", "reasoning")
            aiStudio.putBoolean("avatar_visible", false)
            aiStudio.putLong("history_size", 20L)

            assertEquals("reasoning", aiStudio.observeString("engine").first())
            assertEquals(false, aiStudio.observeBoolean("avatar_visible", default = true).first())
            assertEquals(20L, aiStudio.observeLong("history_size").first())

            assertEquals("", editor.observeString("engine").first())
            assertTrue(editor.observeBoolean("avatar_visible", default = true).first())
            assertFalse(editor.observeBoolean("missing").first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun valuesPersistAcrossFactoryInstances() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("heartbeat-preferences-persist").toString()

        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        DataStorePreferencesStoreFactory(directory, firstScope)
            .forNamespace("settings")
            .putString("language", "ru")
        firstScope.coroutineContext.job.cancelAndJoin()

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            assertEquals(
                "ru",
                DataStorePreferencesStoreFactory(directory, secondScope)
                    .forNamespace("settings")
                    .observeString("language")
                    .first(),
            )
        } finally {
            secondScope.cancel()
        }
    }

    private suspend fun Job.cancelAndJoin() {
        cancel()
        join()
    }
}

