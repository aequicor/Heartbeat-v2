package io.github.aequicor.heartbeat.features.editor.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.create
import com.arkivanov.essenty.lifecycle.destroy
import io.github.aequicor.heartbeat.features.editor.api.EditorAction
import io.github.aequicor.heartbeat.features.editor.api.EditorController
import io.github.aequicor.heartbeat.features.editor.api.EditorControllerFactory
import io.github.aequicor.heartbeat.features.editor.api.EditorEffect
import io.github.aequicor.heartbeat.features.editor.api.EditorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultEditorComponentTest {

    @Test
    fun destroysControllerScopeWithComponent() {
        val lifecycle = LifecycleRegistry()
        val context = DefaultComponentContext(lifecycle)
        var controllerScope: CoroutineScope? = null
        val factory = object : EditorControllerFactory {
            override fun create(noteId: String, scope: CoroutineScope): EditorController {
                controllerScope = scope
                return FakeController()
            }
        }

        DefaultEditorComponent(context, "n1", factory, UnconfinedTestDispatcher())

        val scope = assertNotNull(controllerScope)
        assertTrue(scope.isActive)

        lifecycle.create()
        lifecycle.destroy()

        assertFalse(scope.isActive)
    }

    private class FakeController : EditorController {
        override val state: StateFlow<EditorState> = MutableStateFlow(EditorState())
        override val effects: Flow<EditorEffect> = emptyFlow()

        override fun dispatch(action: EditorAction) = Unit
    }
}
