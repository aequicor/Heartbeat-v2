package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import io.github.aequicor.heartbeat.designsystem.components.HbMenuAction
import io.github.aequicor.heartbeat.designsystem.foundation.HbIconGlyph
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyStatus
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.StepStatus

internal class AuroraWorkMenu {
    private data class Region(val label: String, val bounds: Rect)

    private val regions = mutableMapOf<AiStudioAction, Region>()
    var openAt: ((Offset) -> Unit)? = null
    var beforeDisclosure: ((AiStudioAction) -> Unit)? = null

    fun actionAt(point: Offset, onAction: (AiStudioAction) -> Unit): HbMenuAction? = regions.entries
        .filter { (_, region) -> region.bounds.contains(point) }
        .maxByOrNull { (action) ->
            when {
                action !is AiStudioAction.SetWorkExpanded -> 0
                action.eventId != null -> 2
                else -> 1
            }
        }
        ?.let { (action, region) -> HbMenuAction(region.label, { onAction(action) }, glyph = HbIconGlyph.Minus) }

    @Composable
    fun region(action: AiStudioAction, label: String): Modifier {
        DisposableEffect(this, action) { onDispose { regions.remove(action) } }
        return Modifier.onGloballyPositioned { regions[action] = Region(label, it.boundsInRoot()) }
    }
}

internal fun Reply.fullDisclosureAction(
    expandedWork: Boolean,
    expandedSteps: Set<String>,
    expandedEvents: Set<String>,
    onAction: (AiStudioAction) -> Unit,
): HbMenuAction? {
    if (status == ReplyStatus.Working || steps.isEmpty()) return null
    val fullyExpanded = expandedWork && steps.all { step ->
        val stepKey = "$id/${step.id}"
        val stepExpanded = step.status == StepStatus.Current ||
            step.events.size <= 1 && step.details.isBlank() || stepKey in expandedSteps
        stepExpanded && step.events.all { "$stepKey/${it.id}" in expandedEvents }
    }
    return HbMenuAction(
        if (fullyExpanded) "Свернуть полностью" else "Развернуть полностью",
        { onAction(AiStudioAction.SetWorkExpanded(id, expanded = !fullyExpanded)) },
        glyph = if (fullyExpanded) HbIconGlyph.Minus else HbIconGlyph.ChevronDown,
    )
}
