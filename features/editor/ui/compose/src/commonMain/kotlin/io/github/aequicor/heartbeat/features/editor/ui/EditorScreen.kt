package io.github.aequicor.heartbeat.features.editor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.designsystem.components.HbButton
import io.github.aequicor.heartbeat.designsystem.components.HbText
import io.github.aequicor.heartbeat.designsystem.components.HbTextField
import io.github.aequicor.heartbeat.features.editor.api.EditorAction
import io.github.aequicor.heartbeat.features.editor.api.EditorStore

/**
 * Editor screen bound to the [EditorStore] contract only: it works with
 * any presentation adapter (Decompose component, BLoC) that exposes the
 * store. The integration layer binds the component to this screen.
 */
@Composable
fun EditorScreen(
    store: EditorStore,
    modifier: Modifier = Modifier,
) {
    val state by store.state.collectAsState()

    Column(modifier = modifier) {
        HbTextField(
            value = state.title,
            onValueChange = { store.dispatch(EditorAction.TitleChanged(it)) },
            placeholder = "Заголовок",
            enabled = !state.isLoading,
        )
        HbTextField(
            value = state.content,
            onValueChange = { store.dispatch(EditorAction.ContentChanged(it)) },
            placeholder = "Содержимое",
            enabled = !state.isLoading,
        )
        HbButton(
            text = if (state.isSaving) "Сохранение…" else "Сохранить",
            onClick = { store.dispatch(EditorAction.Save) },
            enabled = !state.isLoading && !state.isSaving,
        )
        state.saveError?.let { error ->
            HbText(text = error)
        }
    }
}
