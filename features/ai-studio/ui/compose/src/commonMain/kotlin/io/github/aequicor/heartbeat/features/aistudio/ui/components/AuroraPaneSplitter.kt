package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.aequicor.heartbeat.designsystem.components.HbOrientation
import io.github.aequicor.heartbeat.designsystem.components.HbSplitter
import io.github.aequicor.heartbeat.designsystem.components.HbSplitterLabels
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.move_pane_splitter_left
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.move_pane_splitter_right
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.reset_pane_width
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AuroraPaneSplitter(
    label: String,
    onResize: (Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HbSplitter(
        HbOrientation.Vertical, onResize, onReset,
        HbSplitterLabels(
            narrower = stringResource(Res.string.move_pane_splitter_left),
            wider = stringResource(Res.string.move_pane_splitter_right),
            reset = stringResource(Res.string.reset_pane_width),
        ),
        modifier.auroraHorizontalResizeCursor().semantics { contentDescription = label },
    )
}

internal expect fun Modifier.auroraHorizontalResizeCursor(): Modifier
