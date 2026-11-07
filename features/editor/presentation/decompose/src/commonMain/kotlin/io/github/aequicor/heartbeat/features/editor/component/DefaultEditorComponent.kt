package io.github.aequicor.heartbeat.features.editor.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import io.github.aequicor.heartbeat.features.editor.api.EditorController
import io.github.aequicor.heartbeat.features.editor.api.EditorControllerFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Creates the controller with a lifecycle-aware scope: the scope is cancelled
 * when the component is destroyed, which stops pending business operations.
 * The UI dispatcher comes from the platform host, so the adapter compiles and
 * runs on every Decompose target, including Linux Native.
 */
class DefaultEditorComponent(
    componentContext: ComponentContext,
    noteId: String,
    factory: EditorControllerFactory,
    dispatcher: CoroutineDispatcher,
) : EditorComponent, ComponentContext by componentContext {

    private val componentScope = CoroutineScope(SupervisorJob() + dispatcher)

    override val controller: EditorController = factory.create(noteId, componentScope)

    init {
        componentContext.lifecycle.doOnDestroy(componentScope::cancel)
    }
}
