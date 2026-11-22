package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.DetailFormat
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.StepStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.WorkEvent
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.WorkEventKind

@Composable
internal fun AuroraWorkDetailContent(event: WorkEvent, touch: Boolean, selection: SelectionState) {
    val color = if (touch) AuroraColors.WorkInk else AuroraColors.DesktopInk
    if (event.details.isBlank()) {
        AuroraWorkText(event.detailText, AuroraWorkRole.Detail, WorkDetailWidth, color = color)
        return
    }
    key(event.id) {
        AuroraReplyReveal(streaming = event.status == StepStatus.Current) {
            when {
                event.detailsFormat == DetailFormat.Markdown -> AuroraMarkdownContent(
                    event.detailText, event.markdownDetails, selection, HbTypography.Detail.copy(color = color), touch,
                    WorkMarkdownIntrinsicSize, streaming = event.status == StepStatus.Current,
                )
                !touch && event.kind == WorkEventKind.Thinking ->
                    AuroraThinkingBody(event.id, event.detailText, selection)
                event.kind == WorkEventKind.Thinking -> {
                    val tail = remember { ThinkingTextTail() }
                    AuroraReplyText(event.detailText, WorkDetailWidth.then(tail.modifier),
                        HbTypography.Detail.copy(color = color), onTextLayout = tail::update)
                }
                else -> AuroraReplyText(event.detailText, WorkDetailWidth, HbTypography.Detail.copy(color = color))
            }
        }
    }
}

// A paragraph wraps within the reply; its unwrapped length must not set the card width.
internal object WorkDetailWidth : LayoutModifier {
    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        measurable.minIntrinsicWidth(height)

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints) =
        measurable.measure(constraints).let { detail ->
            layout(detail.width, detail.height) { detail.placeRelative(0, 0) }
        }
}

// The work body wraps to the reply. Viewports such as LazyColumn cannot answer intrinsic queries.
internal object WorkMarkdownIntrinsicSize : LayoutModifier {
    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int = 0

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int = 0

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int = 0

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int = 0

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints) =
        measurable.measure(constraints).let { detail ->
            layout(detail.width, detail.height) { detail.placeRelative(0, 0) }
        }
}
