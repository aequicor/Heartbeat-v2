package io.github.aequicor.heartbeat.designsystem.patterns

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.designsystem.components.HbDisclosure
import io.github.aequicor.heartbeat.designsystem.components.HbStatusTone

/**
 * Activity of one tool call in the transcript (HB-DS-026).
 *
 * The header names the action and the target and shows the status tone; the input, output and
 * result are revealed by the disclosure. Only statuses the runtime reports are shown: an unknown
 * outcome stays unknown instead of becoming a success. The expanded state is owned by the
 * transcript, so it survives streaming updates and virtualization.
 */
@Composable
fun HbToolActivity(
    title: String,
    target: String,
    status: HbToolStatus,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    stateDescription: String? = null,
    details: @Composable () -> Unit,
) {
    HbDisclosure(
        title = title,
        subtitle = target,
        status = status.tone(),
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier,
        stateDescription = stateDescription,
        content = details,
    )
}

private fun HbToolStatus.tone(): HbStatusTone = when (this) {
    HbToolStatus.Running -> HbStatusTone.Info
    HbToolStatus.Succeeded -> HbStatusTone.Success
    HbToolStatus.Failed -> HbStatusTone.Error
    HbToolStatus.Unknown -> HbStatusTone.Neutral
}
