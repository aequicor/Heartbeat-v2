package io.github.aequicor.heartbeat.features.editor.component

import com.arkivanov.decompose.ComponentContext

/**
 * Creates Decompose components for the shell. [ComponentContext] and [noteId]
 * are runtime parameters, so they stay outside the Metro graph.
 */
interface EditorComponentFactory {
    fun create(componentContext: ComponentContext, noteId: String): EditorComponent
}
