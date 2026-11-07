package io.github.aequicor.heartbeat.features.editor.data.datasource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class EditorDataSourceTest {

    @Test
    @Suppress("InjectDispatcher") // Real threads are the point of this concurrency test.
    fun concurrentWritesKeepEveryNote() = runTest {
        val dataSource = EditorDataSource()

        withContext(Dispatchers.Default) {
            coroutineScope {
                repeat(NOTE_COUNT) { index ->
                    launch { dataSource.store(NoteDto(id = "n$index", title = "t", content = "c")) }
                }
            }
        }

        for (index in 0 until NOTE_COUNT) {
            assertEquals("n$index", dataSource.find("n$index")?.id)
        }
    }

    private companion object {
        private const val NOTE_COUNT = 50
    }
}
