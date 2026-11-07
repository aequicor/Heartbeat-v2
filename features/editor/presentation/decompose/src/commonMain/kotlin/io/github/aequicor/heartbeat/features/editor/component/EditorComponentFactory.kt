package io.github.aequicor.heartbeat.features.editor.component

import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Creates Decompose components for the shell. [ComponentContext], [noteId] and
 * [dispatcher] are runtime parameters, so they stay outside the Metro graph.
 *
 * The platform host provides [dispatcher]: Kotlin/Native targets without a
 * Darwin main loop have no `Dispatchers.Main`, so the universal adapter must not
 * create a UI dispatcher itself.
 */
interface EditorComponentFactory {
    fun create(
        componentContext: ComponentContext,
        noteId: String,
        dispatcher: CoroutineDispatcher,
    ): EditorComponent
}
