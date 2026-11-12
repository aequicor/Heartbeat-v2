package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.designsystem.components.HbDivider
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioState

@Composable
internal fun AiStudioWorkspace(
    state: AiStudioState,
    onAction: (AiStudioAction) -> Unit,
    onShowChats: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        AiStudioHeader(state, onAction, onShowChats)
        HbDivider()
        key(state.selected) {
            AiStudioTranscript(state, onAction, Modifier.weight(1f).fillMaxWidth())
        }
        if (state.session != null) {
            AiStudioComposerBar(state, onAction)
        }
    }
}
