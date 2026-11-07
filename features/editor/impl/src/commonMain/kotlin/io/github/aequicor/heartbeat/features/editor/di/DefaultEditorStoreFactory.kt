package io.github.aequicor.heartbeat.features.editor.di

import dev.zacsweers.metro.ContributesBinding
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.editor.api.EditorStore
import io.github.aequicor.heartbeat.features.editor.api.EditorStoreFactory
import io.github.aequicor.heartbeat.features.editor.domain.usecase.LoadNoteUseCase
import io.github.aequicor.heartbeat.features.editor.domain.usecase.SaveNoteUseCase
import io.github.aequicor.heartbeat.features.editor.presentation.DefaultEditorStore
import kotlinx.coroutines.CoroutineScope

/**
 * Metro bindings of the editor feature. The factory is the feature entry point
 * for presentation adapters: runtime parameters (note id, scope) stay out of
 * the dependency graph.
 */
@ContributesBinding(AppScope::class)
class DefaultEditorStoreFactory(
    private val loadNote: LoadNoteUseCase,
    private val saveNote: SaveNoteUseCase,
) : EditorStoreFactory {

    override fun create(noteId: String, scope: CoroutineScope): EditorStore =
        DefaultEditorStore(loadNote, saveNote, noteId, scope)
}
