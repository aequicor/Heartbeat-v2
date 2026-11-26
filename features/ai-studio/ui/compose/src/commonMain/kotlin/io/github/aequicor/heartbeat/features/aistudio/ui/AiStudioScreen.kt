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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbWindowCaption
import io.github.aequicor.heartbeat.features.aistudio.component.ProjectsSessionsComponent
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Draft
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.LiveText
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraBackdrop
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraDiagramPreviews
import io.github.aequicor.heartbeat.features.aistudio.ui.components.BindAuroraDiagramContext
import io.github.aequicor.heartbeat.features.aistudio.ui.components.LocalDiagramPreviews
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraChats
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraDesktopSidebar
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraFiles
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraPanelDialog
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraViewer
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraWorkspace
import io.github.aequicor.heartbeat.features.aistudio.ui.components.auroraSidebarSurface
import io.github.aequicor.heartbeat.features.aistudio.ui.components.auroraTouchFilesFrame
import io.github.aequicor.heartbeat.features.aistudio.ui.components.rememberArtifactSaveLauncher
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
    projectsSessions: ProjectsSessionsComponent,
    modifier: Modifier = Modifier,
) {
    BindAuroraDiagramContext()
    val saveArtifact = rememberArtifactSaveLauncher { failed -> onAction(AiStudioAction.ArtifactSaveFinished(failed)) }
    LaunchedEffect(state.artifactToSave) {
        state.artifactToSave?.let(saveArtifact)
    }
    val previews = remember(state.chat?.id) { AuroraDiagramPreviews() }
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
                start = panes.leading, end = if (touch) AuroraLayout.Space else Dp.Hairline, top = panes.top,
                bottom = if (touch) AuroraLayout.Space else Dp.Hairline,
            ),
        ) {
            if (panes.sidebar) {
                val captionCut = if (caption.leftInset > Dp.Hairline && panes.top < caption.height) {
                    (caption.leftInset - AuroraLayout.WorkspaceRailWidth).coerceAtLeast(Dp.Hairline)
                } else Dp.Hairline
                AuroraDesktopSidebar(
                    state, onAction, projectsSessions,
                    Modifier.width(AuroraLayout.SidebarWithGap)
                        .auroraSidebarSurface(captionCut, caption.height)
                        .padding(horizontal = AuroraLayout.Space)
                        .padding(top = AuroraLayout.NavigationBrandTop),
                    brandStartInset = captionCut,
                )
            }
            Box(Modifier.weight(1f).fillMaxSize()) {
                CompositionLocalProvider(LocalDiagramPreviews provides previews) {
                    AuroraWorkspace(
                        state, draft, liveText, backdrop, touch, onAction, showNavigation = !panes.sidebar,
                        modifier = Modifier.fillMaxSize(),
                        captionOverlap = panes.captionOverlap,
                        caption = caption,
                        castsSidebarShadow = panes.sidebar,
                    )
                }
                if (state.chatsVisible && !panes.sidebar) {
                    AuroraPanelDialog({ onAction(AiStudioAction.CollapseChats) }) {
                        AuroraChats(state, touch, avatarVisible, onAction, onOpenSettings, projectsSessions,
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
        viewer?.let { file -> AuroraViewer(file, touch, previews) { onAction(AiStudioAction.CloseViewer) } }
    }
}

private class AuroraPaneLayout(
    val sidebar: Boolean,
    val filesAside: Boolean,
    val top: Dp,
    val captionTop: Dp,
    val captionOverlap: Dp,
    private val captionOnLeft: Boolean,
) {
    val leading get() = if (sidebar) Dp.Hairline else AuroraLayout.Space
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
    val top = if (belowCaption) caption.height else if (touch) AuroraLayout.Space else Dp.Hairline
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
