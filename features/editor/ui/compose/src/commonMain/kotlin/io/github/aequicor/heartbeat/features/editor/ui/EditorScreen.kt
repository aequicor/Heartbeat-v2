package io.github.aequicor.heartbeat.features.editor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.designsystem.components.HbButton
import io.github.aequicor.heartbeat.designsystem.components.HbTextField
import io.github.aequicor.heartbeat.features.editor.api.EditorAction
import io.github.aequicor.heartbeat.features.editor.api.EditorController

/**
 * Editor screen bound to the [EditorController] contract only: it works with
 * any presentation adapter (Decompose component, BLoC) that exposes the
 * controller. The integration layer binds the component to this screen.
 */
@Composable
fun EditorScreen(
    controller: EditorController,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()

    Column(modifier = modifier) {
        HbTextField(
            value = state.title,
            onValueChange = { controller.dispatch(EditorAction.TitleChanged(it)) },
            placeholder = "Заголовок",
        )
        HbTextField(
            value = state.content,
            onValueChange = { controller.dispatch(EditorAction.ContentChanged(it)) },
            placeholder = "Содержимое",
        )
        HbButton(
            text = if (state.isSaving) "Сохранение…" else "Сохранить",
            onClick = { controller.dispatch(EditorAction.Save) },
        )
    }
}
