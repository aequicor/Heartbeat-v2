package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotions
import kotlinx.coroutines.delay

/**
 * Copy action with local confirmation (HB-DS-015). The caller performs the copy, so the component
 * does not depend on a clipboard API of a specific renderer; after [onCopy] the label changes to
 * [copiedLabel] for the copy-feedback duration and the change is announced politely without moving
 * the focus.
 */
@Composable
fun HbCopyButton(
    label: String,
    copiedLabel: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(HbMotions.Default.copyFeedbackMs.toLong())
            copied = false
        }
    }

    Box(modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        HbButton(
            text = if (copied) copiedLabel else label,
            onClick = {
                onCopy()
                copied = true
            },
            enabled = enabled,
        )
    }
}
