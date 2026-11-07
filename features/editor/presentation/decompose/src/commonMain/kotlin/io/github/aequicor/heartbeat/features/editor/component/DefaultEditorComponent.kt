package io.github.aequicor.heartbeat.features.editor.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import io.github.aequicor.heartbeat.features.editor.api.EditorController
import io.github.aequicor.heartbeat.features.editor.api.EditorControllerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Creates the controller with a lifecycle-aware scope: the scope is cancelled
 * when the component is destroyed, which stops pending business operations.
 */
class DefaultEditorComponent(
    componentContext: ComponentContext,
    noteId: String,
    factory: EditorControllerFactory,
) : EditorComponent, ComponentContext by componentContext {

    private val componentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val controller: EditorController = factory.create(noteId, componentScope)

    init {
        componentContext.lifecycle.doOnDestroy(componentScope::cancel)
    }
}
