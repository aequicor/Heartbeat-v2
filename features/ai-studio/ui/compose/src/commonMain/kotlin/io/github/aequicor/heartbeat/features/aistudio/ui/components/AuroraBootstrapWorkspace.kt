package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbWindowCaption
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.SessionBootstrapState
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.new_session
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.ungrouped_sessions
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AuroraBootstrapWorkspace(
    state: AiStudioScreenState,
    bootstrap: SessionBootstrapState,
    touch: Boolean,
    showNavigation: Boolean,
    preferredWidth: Dp,
    captionClearance: Dp,
    caption: HbWindowCaption,
    onAction: (AiStudioAction) -> Unit,
) {
    val title = stringResource(Res.string.new_session)
    val project = bootstrap.projectTitle ?: stringResource(Res.string.ungrouped_sessions)
    var chat by remember(bootstrap.id) { mutableStateOf(Chat(bootstrap.id, title, "")) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val corridor = preferredWidth.coerceAtMost(maxWidth)
        val gutter = (maxWidth - corridor) / 2f
        val edgeInset = (AuroraLayout.ContentInset - gutter).coerceAtLeast(Dp.Hairline)
        AuroraHeader(
            state.copy(chat = chat.copy(project = project), subagents = listOf()), touch, showNavigation,
            onAction = { action ->
                when (action) {
                    is AiStudioAction.SetScenario -> chat = chat.copy(scenario = action.scenario)
                    is AiStudioAction.SetAccess -> chat = chat.copy(access = action.access)
                    is AiStudioAction.SetEffort -> chat = chat.copy(effort = action.effort)
                    else -> onAction(action)
                }
            },
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = corridor),
            captionClearance = headerCaptionClearance(captionClearance, gutter + edgeInset),
            captionOnLeft = caption.leftInset > Dp.Hairline,
            edgeInset = edgeInset,
        )
        AuroraBootstrapComposer(
            state, bootstrap, chat, touch, maxHeight * BootstrapComposerHeightFraction, onAction,
            Modifier.align(Alignment.BottomCenter).auroraLowerLayout(touch, corridor),
        )
    }
}

private const val BootstrapComposerHeightFraction = 0.38f
