package io.github.aequicor.heartbeat.features.editor.di

import dev.zacsweers.metro.ContributesBinding
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.editor.api.EditorController
import io.github.aequicor.heartbeat.features.editor.api.EditorControllerFactory
import io.github.aequicor.heartbeat.features.editor.domain.usecase.LoadNote
import io.github.aequicor.heartbeat.features.editor.domain.usecase.SaveNote
import io.github.aequicor.heartbeat.features.editor.presentation.DefaultEditorController
import kotlinx.coroutines.CoroutineScope

/**
 * Metro bindings of the editor feature. The factory is the feature entry point
 * for presentation adapters: runtime parameters (note id, scope) stay out of
 * the dependency graph.
 */
@ContributesBinding(AppScope::class)
class DefaultEditorControllerFactory(
    private val loadNote: LoadNote,
    private val saveNote: SaveNote,
) : EditorControllerFactory {

    override fun create(noteId: String, scope: CoroutineScope): EditorController =
        DefaultEditorController(loadNote, saveNote, noteId, scope)
}
