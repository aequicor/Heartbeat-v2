package io.github.aequicor.heartbeat.features.editor

import io.github.aequicor.heartbeat.features.editor.api.EditorAction
import io.github.aequicor.heartbeat.features.editor.api.EditorEffect
import io.github.aequicor.heartbeat.features.editor.data.datasource.EditorDataSource
import io.github.aequicor.heartbeat.features.editor.data.repository.DefaultEditorRepository
import io.github.aequicor.heartbeat.features.editor.domain.model.Note
import io.github.aequicor.heartbeat.features.editor.domain.repository.EditorRepository
import io.github.aequicor.heartbeat.features.editor.domain.usecase.LoadNoteUseCase
import io.github.aequicor.heartbeat.features.editor.domain.usecase.SaveNoteUseCase
import io.github.aequicor.heartbeat.features.editor.presentation.DefaultEditorStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultEditorStoreTest {

    @Test
    fun storeStartsInLoadingState() = runTest {
        val store = store(repositoryWith(), "n1")

        assertTrue(store.state.value.isLoading)

        advanceUntilIdle()

        assertFalse(store.state.value.isLoading)
    }

    @Test
    fun loadsNoteOnCreate() = runTest {
        val repository = repositoryWith(Note("n1", "Заголовок", "Содержимое"))
        val store = store(repository, "n1")

        advanceUntilIdle()

        assertEquals("Заголовок", store.state.value.title)
        assertEquals("Содержимое", store.state.value.content)
    }

    @Test
    fun missingNoteHasEmptyContent() = runTest {
        val store = store(repositoryWith(), "n1")

        advanceUntilIdle()

        assertEquals("", store.state.value.title)
        assertFalse(store.state.value.isLoading)
    }

    @Test
    fun editBeforeLoadIsIgnored() = runTest {
        val repository = FakeEditorRepository(loadDelayMillis = 1_000).apply {
            seed(Note("n1", "Загруженный", ""))
        }
        val store = store(repository, "n1")

        store.dispatch(EditorAction.TitleChanged("Ранняя правка"))
        advanceUntilIdle()

        assertEquals("Загруженный", store.state.value.title)
    }

    @Test
    fun loadErrorDoesNotBlockTheStore() = runTest {
        val repository = FakeEditorRepository(loadFailures = 1).apply {
            seed(Note("n1", "Загруженный", ""))
        }
        val store = store(repository, "n1")

        advanceUntilIdle()

        assertEquals("network is down", store.state.value.loadError)
        assertFalse(store.state.value.isLoading)
    }

    @Test
    fun reloadAfterLoadErrorLoadsTheNote() = runTest {
        val repository = FakeEditorRepository(loadFailures = 1).apply {
            seed(Note("n1", "Загруженный", ""))
        }
        val store = store(repository, "n1")
        advanceUntilIdle()

        store.dispatch(EditorAction.Reload)
        advanceUntilIdle()

        assertEquals("Загруженный", store.state.value.title)
        assertNull(store.state.value.loadError)
    }

    @Test
    fun editAfterLoadErrorIsIgnored() = runTest {
        val store = store(FakeEditorRepository(loadFailures = 1), "n1")
        advanceUntilIdle()

        store.dispatch(EditorAction.TitleChanged("Правка без загрузки"))

        assertEquals("", store.state.value.title)
        assertEquals("network is down", store.state.value.loadError)
    }

    @Test
    fun titleChangeUpdatesState() = runTest {
        val store = store(repositoryWith(), "n1")
        advanceUntilIdle()

        store.dispatch(EditorAction.TitleChanged("Новый заголовок"))

        assertEquals("Новый заголовок", store.state.value.title)
    }

    @Test
    fun savePersistsAndEmitsEffect() = runTest {
        val repository = repositoryWith()
        val store = store(repository, "n1")
        advanceUntilIdle()
        store.dispatch(EditorAction.TitleChanged("Заголовок"))
        store.dispatch(EditorAction.ContentChanged("Текст"))

        val effect = async { store.effects.first() }
        store.dispatch(EditorAction.Save)
        advanceUntilIdle()

        assertEquals(EditorEffect.Saved, effect.await())
        assertEquals(Note("n1", "Заголовок", "Текст"), repository.load("n1"))
        assertFalse(store.state.value.isSaving)
        assertNull(store.state.value.saveError)
    }

    @Test
    fun saveFailureSetsErrorAndClearsSaving() = runTest {
        val repository = FakeEditorRepository(saveFailure = IllegalStateException("disk full"))
        val store = store(repository, "n1")
        advanceUntilIdle()

        store.dispatch(EditorAction.Save)
        advanceUntilIdle()

        assertEquals("disk full", store.state.value.saveError)
        assertFalse(store.state.value.isSaving)
    }

    @Test
    fun editDoesNotClearSaveError() = runTest {
        val repository = FakeEditorRepository(saveFailure = IllegalStateException("disk full"))
        val store = store(repository, "n1")
        advanceUntilIdle()
        store.dispatch(EditorAction.Save)
        advanceUntilIdle()

        store.dispatch(EditorAction.TitleChanged("Правка после ошибки"))

        assertEquals("Правка после ошибки", store.state.value.title)
        assertEquals("disk full", store.state.value.saveError)
    }

    @Test
    fun repeatedSaveRunsOnce() = runTest {
        val repository = FakeEditorRepository()
        val store = store(repository, "n1")
        advanceUntilIdle()

        store.dispatch(EditorAction.Save)
        store.dispatch(EditorAction.Save)
        advanceUntilIdle()

        assertEquals(1, repository.saveCalls)
    }

    @Test
    @Suppress("InjectDispatcher")
    fun concurrentSaveStartsOnlyOnce() = runTest {
        val repository = FakeEditorRepository(saveDelayMillis = 1_000)
        val store = store(repository, "n1")
        advanceUntilIdle()

        // Release many dispatchers on a real thread pool at once: the atomic
        // check-and-set in the store must let exactly one save through.
        val attempts = 16
        val start = CompletableDeferred<Unit>()
        coroutineScope {
            repeat(attempts) {
                launch(Dispatchers.Default) {
                    start.await()
                    store.dispatch(EditorAction.Save)
                }
            }
            start.complete(Unit)
        }
        advanceUntilIdle()

        assertEquals(1, repository.saveCalls)
        assertFalse(store.state.value.isSaving)
    }

    @Test
    @Suppress("InjectDispatcher")
    fun concurrentEditAndSaveNeverApplyAnEditDuringTheSave() = runTest {
        val editsDuringSave = mutableListOf<String>()
        lateinit var editor: DefaultEditorStore
        val repository = FakeEditorRepository(
            onSave = { note ->
                // The save coroutine runs only after the store claimed isSaving, so any edit that
                // changed the title at this point violates the ignore-edits-during-save contract.
                val state = editor.state.value
                if (state.isSaving && state.title != note.title) {
                    editsDuringSave += "saved '${note.title}' while state had '${state.title}'"
                }
            },
        )
        editor = store(repository, "n1")
        advanceUntilIdle()

        // Real threads release an edit and a save at the same moment. The edit must land either
        // before the save claim (then the save persists it) or not at all, and the Save must never
        // be dropped because an edit won the state race.
        val attempts = 250
        repeat(attempts) { attempt ->
            editor.dispatch(EditorAction.TitleChanged("snapshot-$attempt"))

            val start = CompletableDeferred<Unit>()
            coroutineScope {
                launch(Dispatchers.Default) {
                    start.await()
                    editor.dispatch(EditorAction.Save)
                }
                launch(Dispatchers.Default) {
                    start.await()
                    editor.dispatch(EditorAction.TitleChanged("concurrent-$attempt"))
                }
                start.complete(Unit)
            }
            advanceUntilIdle()

            assertFalse(editor.state.value.isSaving, "attempt $attempt: save must finish")
        }

        assertTrue(editsDuringSave.isEmpty(), "edits during save: $editsDuringSave")
        assertEquals(attempts, repository.saveCalls)
    }

    @Test
    fun savePersistsSnapshotAndIgnoresEditsWhileRunning() = runTest {
        val repository = FakeEditorRepository(saveDelayMillis = 1_000)
        val store = store(repository, "n1")
        advanceUntilIdle()
        store.dispatch(EditorAction.TitleChanged("Снимок"))

        store.dispatch(EditorAction.Save)
        advanceTimeBy(100)
        assertTrue(store.state.value.isSaving)

        store.dispatch(EditorAction.TitleChanged("Правка во время сохранения"))
        advanceUntilIdle()

        assertEquals("Снимок", store.state.value.title)
        assertEquals("Снимок", repository.load("n1").title)
    }

    @Test
    fun savedEffectIsNotReplayedToALateCollector() = runTest {
        val store = store(FakeEditorRepository(), "n1")
        advanceUntilIdle()
        store.dispatch(EditorAction.Save)
        advanceUntilIdle()

        // The store documented effects as best-effort hints: a subscriber that arrives after
        // the save must not receive the past Saved event and act on stale navigation.
        val late = async { withTimeoutOrNull(1) { store.effects.first() } }
        advanceUntilIdle()

        assertNull(late.await())
    }

    @Test
    fun savesWithoutEffectCollectorDoNotBlock() = runTest {
        val repository = FakeEditorRepository()
        val store = store(repository, "n1")
        advanceUntilIdle()

        repeat(100) { attempt ->
            store.dispatch(EditorAction.TitleChanged("Заголовок $attempt"))
            store.dispatch(EditorAction.Save)
            advanceUntilIdle()

            assertFalse(store.state.value.isSaving)
        }

        assertEquals(100, repository.saveCalls)
    }

    @Test
    fun reloadWithoutLoadErrorIsIgnored() = runTest {
        val store = store(repositoryWith(), "n1")
        advanceUntilIdle()
        store.dispatch(EditorAction.TitleChanged("Правка"))

        store.dispatch(EditorAction.Reload)
        advanceUntilIdle()

        assertEquals("Правка", store.state.value.title)
    }

    @Test
    fun reloadDuringSaveDoesNotOverwriteState() = runTest {
        val repository = FakeEditorRepository(saveDelayMillis = 1_000)
        val store = store(repository, "n1")
        advanceUntilIdle()
        store.dispatch(EditorAction.TitleChanged("Снимок"))

        store.dispatch(EditorAction.Save)
        advanceTimeBy(100)
        assertTrue(store.state.value.isSaving)

        store.dispatch(EditorAction.Reload)
        advanceUntilIdle()

        assertEquals("Снимок", store.state.value.title)
        assertFalse(store.state.value.isSaving)
        assertEquals(1, repository.saveCalls)
    }

    private suspend fun repositoryWith(vararg notes: Note): DefaultEditorRepository {
        val repository = DefaultEditorRepository(EditorDataSource())
        for (note in notes) {
            repository.save(note)
        }
        return repository
    }

    private fun TestScope.store(
        repository: EditorRepository,
        noteId: String,
    ): DefaultEditorStore =
        DefaultEditorStore(
            loadNote = LoadNoteUseCase(repository),
            saveNote = SaveNoteUseCase(repository),
            noteId = noteId,
            scope = this,
        )
}

/** In-memory repository with configurable delays and failures. */
private class FakeEditorRepository(
    private val loadDelayMillis: Long = 0,
    private val saveDelayMillis: Long = 0,
    private val saveFailure: Throwable? = null,
    loadFailures: Int = 0,
    private val onSave: (Note) -> Unit = {},
) : EditorRepository {

    private val notes = mutableMapOf<String, Note>()
    private var remainingLoadFailures = loadFailures

    var saveCalls = 0
        private set

    fun seed(note: Note) {
        notes[note.id] = note
    }

    override suspend fun load(noteId: String): Note {
        if (loadDelayMillis > 0) {
            delay(loadDelayMillis)
        }
        if (remainingLoadFailures > 0) {
            remainingLoadFailures--
            error("network is down")
        }
        return notes[noteId] ?: Note(id = noteId, title = "", content = "")
    }

    override suspend fun save(note: Note) {
        saveCalls++
        onSave(note)
        if (saveDelayMillis > 0) {
            delay(saveDelayMillis)
        }
        val failure = saveFailure
        if (failure != null) {
            throw failure
        }
        notes[note.id] = note
    }
}
