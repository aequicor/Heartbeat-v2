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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbPaneGeometry
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
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraPaneSplitter
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraPanelDialog
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraViewer
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraWorkspace
import io.github.aequicor.heartbeat.features.aistudio.ui.components.auroraSidebarSurface
import io.github.aequicor.heartbeat.features.aistudio.ui.components.auroraTouchFilesFrame
import io.github.aequicor.heartbeat.features.aistudio.ui.components.rememberArtifactSaveLauncher
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.resize_files_pane
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.resize_projects_sessions_pane
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource

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
    val splitterWidth = HbSizes.minimumTarget
    val touch = splitterWidth >= AuroraLayout.TouchTarget
    val towardsEnd = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    var preferredSidebarWidth by rememberSaveable { mutableFloatStateOf(AuroraLayout.SidebarWithGap.value) }
    var preferredFilesWidth by rememberSaveable { mutableFloatStateOf(AuroraLayout.FilesPane.value) }
    val caption = HbWindowCaption.current
    val canvas = if (touch) AuroraColors.Canvas else AuroraColors.DesktopCanvas
    val viewer = state.viewer
    val hiddenSemantics = Modifier.clearAndSetSemantics {
        hideFromAccessibility()
    }
    val backgroundSemantics = if (viewer == null) Modifier else hiddenSemantics
    val workspaceSemantics = if (touch && state.filesVisible) hiddenSemantics else backgroundSemantics
    BoxWithConstraints(modifier.fillMaxSize().background(canvas).imePadding()) {
        val panes = auroraPaneLayout(
            maxWidth, touch, state.filesVisible, caption, splitterWidth,
            preferredSidebarWidth.dp, preferredFilesWidth.dp,
        )
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
                    Modifier.width(panes.sidebarWidth)
                        .auroraSidebarSurface(captionCut, caption.height)
                        .padding(horizontal = AuroraLayout.Space)
                        .padding(top = AuroraLayout.NavigationBrandTop),
                    brandStartInset = captionCut,
                )
                AuroraPaneSplitter(
                    label = stringResource(Res.string.resize_projects_sessions_pane),
                    onResize = { delta ->
                        preferredSidebarWidth = resizePane(
                            preferredSidebarWidth, delta * towardsEnd, SidebarMinWidth, panes.sidebarResizeMax,
                        )
                    },
                    onReset = { preferredSidebarWidth = AuroraLayout.SidebarWithGap.value },
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
                AuroraPaneSplitter(
                    label = stringResource(Res.string.resize_files_pane),
                    onResize = { delta ->
                        preferredFilesWidth = resizePane(
                            preferredFilesWidth, -delta * towardsEnd, FilesMinWidth, panes.filesResizeMax,
                        )
                    },
                    onReset = { preferredFilesWidth = AuroraLayout.FilesPane.value },
                    modifier = Modifier.padding(top = panes.filesTop),
                )
                AuroraFiles(state, touch, onAction, Modifier.padding(top = panes.filesTop).width(panes.filesWidth))
            }
        }
        viewer?.let { file -> AuroraViewer(file, touch, previews) { onAction(AiStudioAction.CloseViewer) } }
    }
}

private class AuroraPaneLayout(
    val sidebarWidth: Dp,
    val filesWidth: Dp,
    val sidebarResizeMax: Dp,
    val filesResizeMax: Dp,
    val top: Dp,
    val captionTop: Dp,
    val captionOverlap: Dp,
    private val captionOnLeft: Boolean,
) {

    val sidebar get() = sidebarWidth > Dp.Hairline
    val filesAside get() = filesWidth > Dp.Hairline
    val leading get() = if (sidebar) Dp.Hairline else AuroraLayout.Space
    val filesTop get() = if (captionOnLeft) Dp.Hairline else captionTop
}

private fun auroraPaneLayout(
    width: Dp,
    touch: Boolean,
    filesVisible: Boolean,
    caption: HbWindowCaption,
    splitterWidth: Dp,
    preferredSidebarWidth: Dp,
    preferredFilesWidth: Dp,
): AuroraPaneLayout {
    val sidebar = !touch && width >= AuroraLayout.NavBreakpoint &&
        width >= WorkspaceMinWidth + SidebarMinWidth + splitterWidth
    val leading = if (sidebar) Dp.Hairline else AuroraLayout.Space
    val filesBreakpoint = if (sidebar) AuroraLayout.ThreePaneBreakpoint else AuroraLayout.TwoPaneBreakpoint
    val sidebarMinimum = if (sidebar) SidebarMinWidth + splitterWidth else Dp.Hairline
    val sideSpace = width - leading - WorkspaceMinWidth
    val filesAside = filesVisible && !touch && width >= filesBreakpoint &&
        sideSpace >= sidebarMinimum + FilesMinWidth + splitterWidth
    val sidebarSplitter = if (sidebar) splitterWidth else Dp.Hairline
    val filesSplitter = if (filesAside) splitterWidth else Dp.Hairline
    val paneBudget = sideSpace - sidebarSplitter - filesSplitter
    val minimumFiles = if (filesAside) FilesMinWidth else Dp.Hairline
    val sidebarWidth = if (sidebar) {
        preferredSidebarWidth.coerceIn(SidebarMinWidth, minOf(SidebarMaxWidth, paneBudget - minimumFiles))
    } else Dp.Hairline
    val filesMax = minOf(FilesMaxWidth, paneBudget - sidebarWidth)
    val filesWidth = if (filesAside) preferredFilesWidth.coerceIn(FilesMinWidth, filesMax) else Dp.Hairline
    val sidebarMax = minOf(SidebarMaxWidth, paneBudget - filesWidth)
    val filesSpace = filesWidth + filesSplitter
    val sideInset = maxOf(caption.leftInset, caption.rightInset)
    val belowCaption = sideInset > Dp.Hairline && width < sideInset + AuroraLayout.NavigationWidth
    val top = if (belowCaption) caption.height else if (touch) AuroraLayout.Space else Dp.Hairline
    val captionTop = (caption.height - top).coerceAtLeast(Dp.Hairline)
    val overlap = if (belowCaption) Dp.Hairline else auroraCaptionOverlap(caption, sidebar, filesSpace)
    return AuroraPaneLayout(
        sidebarWidth, filesWidth, sidebarMax, filesMax, top, captionTop, overlap, caption.leftInset > Dp.Hairline,
    )
}

private fun resizePane(preferred: Float, delta: Int, minimum: Dp, maximum: Dp): Float {
    val current = preferred.dp.coerceIn(minimum, maximum)
    val resized = (current + delta.dp).coerceIn(minimum, maximum)
    return if (resized == current) preferred else resized.value
}

private val SidebarMinWidth = 192.dp
private val SidebarMaxWidth = HbPaneGeometry.SidebarMax.dp
private val FilesMinWidth = HbPaneGeometry.InspectorMin.dp
private val FilesMaxWidth = HbPaneGeometry.InspectorMax.dp
private val WorkspaceMinWidth = HbPaneGeometry.SplitPaneMin.dp

private fun auroraCaptionOverlap(caption: HbWindowCaption, sidebar: Boolean, filesSpace: Dp): Dp = when {
    caption.leftInset <= Dp.Hairline ->
        (caption.rightInset - AuroraLayout.Space - filesSpace).coerceAtLeast(Dp.Hairline)
    sidebar -> Dp.Hairline
    else -> (caption.leftInset - AuroraLayout.Space).coerceAtLeast(Dp.Hairline)
}
