package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.designsystem.components.HbDivider

/** Main pane of the studio: conversation header, transcript and composer. */
@Composable
internal fun AiStudioWorkspace(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        AiStudioHeader()
        HbDivider()
        AiStudioTranscript(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        AiStudioComposerBar(modifier = Modifier.fillMaxWidth())
    }
}
