package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
 * Copy action with confirmation (HB-DS-015). The caller performs the copy and reports its outcome,
 * so the component does not depend on a clipboard API of a specific renderer.
 *
 * When [copied] is `null`, the component shows [copiedLabel] only after [onCopy] reports success and
 * keeps it for the copy-feedback duration; this is the self-managed mode for a copy that completes
 * synchronously. When the owner passes [copied], the component only reflects the confirmed result of
 * [onCopy], so a failed copy never announces success. Each successful copy restarts the confirmation
 * timer, and the change is announced politely without moving the focus.
 */
@Composable
fun HbCopyButton(
    label: String,
    copiedLabel: String,
    onCopy: () -> Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    copied: Boolean? = null,
) {
    var confirmationKey by remember { mutableIntStateOf(0) }
    var localCopied by remember { mutableStateOf(false) }
    val shownCopied = copied ?: localCopied

    LaunchedEffect(confirmationKey) {
        if (confirmationKey == 0) return@LaunchedEffect
        localCopied = true
        delay(HbMotions.Default.copyFeedbackMs.toLong())
        localCopied = false
    }

    Box(modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        HbButton(
            text = if (shownCopied) copiedLabel else label,
            onClick = {
                val success = onCopy()
                if (copied == null && success) confirmationKey++
            },
            enabled = enabled,
        )
    }
}
