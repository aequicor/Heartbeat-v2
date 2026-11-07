package io.github.aequicor.heartbeat.features.editor.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.create
import com.arkivanov.essenty.lifecycle.destroy
import io.github.aequicor.heartbeat.features.editor.api.EditorAction
import io.github.aequicor.heartbeat.features.editor.api.EditorEffect
import io.github.aequicor.heartbeat.features.editor.api.EditorState
import io.github.aequicor.heartbeat.features.editor.api.EditorStore
import io.github.aequicor.heartbeat.features.editor.api.EditorStoreFactory
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
    fun destroysStoreScopeWithComponent() {
        val lifecycle = LifecycleRegistry()
        val context = DefaultComponentContext(lifecycle)
        var storeScope: CoroutineScope? = null
        val factory = object : EditorStoreFactory {
            override fun create(noteId: String, scope: CoroutineScope): EditorStore {
                storeScope = scope
                return FakeStore()
            }
        }

        DefaultEditorComponent(context, "n1", factory, UnconfinedTestDispatcher())

        val scope = assertNotNull(storeScope)
        assertTrue(scope.isActive)

        lifecycle.create()
        lifecycle.destroy()

        assertFalse(scope.isActive)
    }

    private class FakeStore : EditorStore {
        override val state: StateFlow<EditorState> = MutableStateFlow(EditorState())
        override val effects: Flow<EditorEffect> = emptyFlow()

        override fun dispatch(action: EditorAction) = Unit
    }
}
