@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbWindowCaption
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Draft
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.LiveText
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraBackdrop
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraChats
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraDesktopSidebar
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraFiles
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraPanelDialog
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraViewer
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraWorkspace
import io.github.aequicor.heartbeat.features.aistudio.ui.components.auroraTouchFilesFrame
import kotlinx.coroutines.flow.StateFlow

/** Displays the prepared Aurora workspace and forwards interactions to its Decompose component. */
@Composable
internal fun AiStudioScreen(
    state: AiStudioScreenState,
    draft: StateFlow<Draft>,
    liveText: StateFlow<LiveText>,
    backdrop: AuroraBackdrop?,
    onAction: (AiStudioAction) -> Unit,
    onOpenSettings: () -> Unit,
    avatarVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val touch = HbSizes.minimumTarget >= AuroraLayout.TouchTarget
    val caption = HbWindowCaption.current
    val canvas = if (touch) AuroraColors.Canvas else AuroraColors.DesktopCanvas
    val viewer = state.viewer
    val hiddenSemantics = Modifier.clearAndSetSemantics {
        hideFromAccessibility()
    }
    val backgroundSemantics = if (viewer == null) Modifier else hiddenSemantics
    val workspaceSemantics = if (touch && state.filesVisible) hiddenSemantics else backgroundSemantics
    BoxWithConstraints(modifier.fillMaxSize().background(canvas).imePadding()) {
        val panes = auroraPaneLayout(maxWidth, touch, state.filesVisible, caption)
        Row(
            Modifier.fillMaxSize().then(workspaceSemantics).padding(
                start = panes.leading, end = AuroraLayout.Space, top = panes.top, bottom = AuroraLayout.Space,
            ),
        ) {
            if (panes.sidebar) {
                AuroraDesktopSidebar(
                    state, onAction, onOpenSettings,
                    Modifier.width(AuroraLayout.SidebarWithGap).padding(top = panes.sidebarTop),
                )
            }
            Box(Modifier.weight(1f).fillMaxSize()) {
                AuroraWorkspace(
                    state, draft, liveText, backdrop, touch, onAction, showNavigation = !panes.sidebar,
                    modifier = Modifier.fillMaxSize(),
                    captionOverlap = panes.captionOverlap,
                    caption = caption,
                )
                if (state.chatsVisible && !panes.sidebar) {
                    AuroraPanelDialog({ onAction(AiStudioAction.ToggleChats(false)) }) {
                        AuroraChats(state, touch, avatarVisible, onAction, onOpenSettings,
                            Modifier.fillMaxSize().then(workspaceSemantics).padding(AuroraLayout.Space), close = true)
                    }
                }
                if (state.filesVisible && !panes.filesAside) {
                    AuroraPanelDialog({ onAction(AiStudioAction.ToggleFiles(false)) }) {
                        val panelModifier = if (touch) backgroundSemantics.auroraTouchFilesFrame()
                        else Modifier.fillMaxSize().then(backgroundSemantics)
                        AuroraFiles(state, touch, onAction, panelModifier)
                    }
                }
            }
            if (panes.filesAside) {
                AuroraFiles(
                    state, touch, onAction,
                    Modifier.padding(start = AuroraLayout.PaneGap, top = panes.filesTop).width(AuroraLayout.FilesPane),
                )
            }
        }
        viewer?.let { file -> AuroraViewer(file, touch, { onAction(AiStudioAction.CloseViewer) }) }
    }
}

private class AuroraPaneLayout(
    val sidebar: Boolean,
    val filesAside: Boolean,
    val top: Dp,
    private val captionTop: Dp,
    val captionOverlap: Dp,
    private val captionOnLeft: Boolean,
) {
    val leading get() = if (sidebar) AuroraLayout.PaneGap else AuroraLayout.Space
    val sidebarTop get() = if (captionOnLeft) captionTop else Dp.Hairline
    val filesTop get() = if (captionOnLeft) Dp.Hairline else captionTop
}

private fun auroraPaneLayout(
    width: Dp,
    touch: Boolean,
    filesVisible: Boolean,
    caption: HbWindowCaption,
): AuroraPaneLayout {
    val sidebar = !touch && width >= AuroraLayout.NavBreakpoint
    val filesBreakpoint = if (sidebar) AuroraLayout.ThreePaneBreakpoint else AuroraLayout.TwoPaneBreakpoint
    val filesAside = filesVisible && !touch && width >= filesBreakpoint
    val filesSpace = if (filesAside) AuroraLayout.FilesPane + AuroraLayout.PaneGap else Dp.Hairline
    val sideInset = maxOf(caption.leftInset, caption.rightInset)
    val belowCaption = sideInset > Dp.Hairline && width < sideInset + AuroraLayout.NavigationWidth
    val top = if (belowCaption) caption.height else AuroraLayout.Space
    val captionTop = (caption.height - top).coerceAtLeast(Dp.Hairline)
    val overlap = if (belowCaption) Dp.Hairline else auroraCaptionOverlap(caption, sidebar, filesSpace)
    return AuroraPaneLayout(sidebar, filesAside, top, captionTop, overlap, caption.leftInset > Dp.Hairline)
}

private fun auroraCaptionOverlap(caption: HbWindowCaption, sidebar: Boolean, filesSpace: Dp): Dp = when {
    caption.leftInset <= Dp.Hairline ->
        (caption.rightInset - AuroraLayout.Space - filesSpace).coerceAtLeast(Dp.Hairline)
    sidebar -> Dp.Hairline
    else -> (caption.leftInset - AuroraLayout.Space).coerceAtLeast(Dp.Hairline)
}
