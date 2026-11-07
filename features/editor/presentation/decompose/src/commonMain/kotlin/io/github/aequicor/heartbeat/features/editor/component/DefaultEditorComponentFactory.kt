package io.github.aequicor.heartbeat.features.editor.component

import com.arkivanov.decompose.ComponentContext
import dev.zacsweers.metro.ContributesBinding
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.editor.api.EditorControllerFactory
import kotlinx.coroutines.CoroutineDispatcher

/** Metro bindings of the Decompose adapter. */
@ContributesBinding(AppScope::class)
class DefaultEditorComponentFactory(
    private val controllerFactory: EditorControllerFactory,
) : EditorComponentFactory {

    override fun create(
        componentContext: ComponentContext,
        noteId: String,
        dispatcher: CoroutineDispatcher,
    ): EditorComponent =
        DefaultEditorComponent(componentContext, noteId, controllerFactory, dispatcher)
}
