package io.github.aequicor.heartbeat.features.editor

import io.github.aequicor.heartbeat.features.editor.api.EditorAction
import io.github.aequicor.heartbeat.features.editor.api.EditorEffect
import io.github.aequicor.heartbeat.features.editor.api.EditorState
import io.github.aequicor.heartbeat.features.editor.data.datasource.EditorDataSource
import io.github.aequicor.heartbeat.features.editor.data.repository.DefaultEditorRepository
import io.github.aequicor.heartbeat.features.editor.domain.model.Note
import io.github.aequicor.heartbeat.features.editor.domain.usecase.LoadNote
import io.github.aequicor.heartbeat.features.editor.domain.usecase.SaveNote
import io.github.aequicor.heartbeat.features.editor.presentation.DefaultEditorController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultEditorControllerTest {

    @Test
    fun loadsNoteOnCreate() = runTest {
        val repository = repositoryWith(Note("n1", "Заголовок", "Содержимое"))
        val controller = controller(repository, "n1")

        advanceUntilIdle()

        assertEquals("Заголовок", controller.state.value.title)
        assertEquals("Содержимое", controller.state.value.content)
    }

    @Test
    fun missingNoteStartsWithEmptyState() = runTest {
        val controller = controller(repositoryWith(), "n1")

        advanceUntilIdle()

        assertEquals(EditorState(), controller.state.value)
    }

    @Test
    fun titleChangeUpdatesState() = runTest {
        val controller = controller(repositoryWith(), "n1")
        advanceUntilIdle()

        controller.dispatch(EditorAction.TitleChanged("Новый заголовок"))

        assertEquals("Новый заголовок", controller.state.value.title)
    }

    @Test
    fun savePersistsAndEmitsEffect() = runTest {
        val repository = repositoryWith()
        val controller = controller(repository, "n1")
        advanceUntilIdle()
        controller.dispatch(EditorAction.TitleChanged("Заголовок"))
        controller.dispatch(EditorAction.ContentChanged("Текст"))

        val effect = async { controller.effects.first() }
        controller.dispatch(EditorAction.Save)
        advanceUntilIdle()

        assertEquals(EditorEffect.Saved, effect.await())
        assertEquals(Note("n1", "Заголовок", "Текст"), repository.load("n1"))
        assertFalse(controller.state.value.isSaving)
    }

    private suspend fun repositoryWith(vararg notes: Note): DefaultEditorRepository {
        val repository = DefaultEditorRepository(EditorDataSource())
        for (note in notes) {
            repository.save(note)
        }
        return repository
    }

    private fun TestScope.controller(
        repository: DefaultEditorRepository,
        noteId: String,
    ): DefaultEditorController =
        DefaultEditorController(
            loadNote = LoadNote(repository),
            saveNote = SaveNote(repository),
            noteId = noteId,
            scope = this,
        )
}
