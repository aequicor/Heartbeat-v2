package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraColors as AuroraColors
import io.github.aequicor.heartbeat.designsystem.theme.HbAuroraLayout as AuroraLayout
import io.github.aequicor.heartbeat.features.aistudio.component.ProjectsSessionsComponent
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.NavigationKind
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.NavigationLoadError
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.NavigationRow
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.navigation_copy_failed
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.navigation_copy_logs
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.navigation_logs_copied
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.navigation_page_failed
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.navigation_projects_failed
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.navigation_sessions_failed
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.navigation_retry_page
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.navigation_retrying_page
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AuroraNavigationPage(
    row: NavigationRow,
    list: LazyListState,
    touch: Boolean,
    projectsSessions: ProjectsSessionsComponent,
    modifier: Modifier = Modifier,
) {
    val failure = row.loadError
    if (failure != null) {
        val inset = when (row.kind) {
            NavigationKind.More -> AuroraLayout.DocumentListIndent
            NavigationKind.MoreChildren -> AuroraLayout.NavigationChildIndent
            else -> Dp.Hairline
        }
        val title = when {
            row.kind != NavigationKind.MoreProjects -> Res.string.navigation_sessions_failed
            failure.firstPage -> Res.string.navigation_projects_failed
            else -> Res.string.navigation_page_failed
        }
        AuroraNavigationPageError(
            stringResource(title), failure, touch,
            { projectsSessions.retryPage(row.id) }, modifier.padding(start = inset),
        )
        return
    }
    val load = { row.loadNextPage(projectsSessions) }
    if (row.kind == NavigationKind.MoreProjects && row.page != null) {
        LaunchedEffect(list, row.id, row.page) {
            snapshotFlow { list.layoutInfo.visibleItemsInfo.any { it.key == row.id } }.first { it }
            load()
        }
    }
    if (touch) {
        AuroraControl(load, modifier.fillMaxWidth(), touch = true, background = Color.Transparent, round = false) {
            AuroraText(row.displayTitle())
        }
    } else {
        AuroraMoreChats(row.displayTitle(), load, modifier)
    }
}

@Composable
private fun AuroraNavigationPageError(
    title: String,
    error: NavigationLoadError,
    touch: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    var copyStatus by remember(error.diagnostic) { mutableStateOf(CopyStatus.Ready) }
    LaunchedEffect(copyStatus) {
        if (copyStatus == CopyStatus.Copied) {
            delay(CopyFeedbackMillis)
            copyStatus = CopyStatus.Ready
        }
    }
    Column(
        modifier.fillMaxWidth().background(AuroraColors.WorkHover, RoundedCornerShape(AuroraLayout.SmallRadius))
            .padding(horizontal = AuroraLayout.ContentInset, vertical = AuroraLayout.Space),
        verticalArrangement = Arrangement.spacedBy(AuroraLayout.SmallGap),
    ) {
        AuroraText(title, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = AuroraColors.DesktopInk)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AuroraLayout.SmallGap)) {
            AuroraControl(
                onRetry,
                Modifier.border(
                    AuroraLayout.Stroke, AuroraColors.NavigationSearchOutline,
                    RoundedCornerShape(AuroraLayout.SmallRadius),
                ),
                touch = touch, enabled = !error.retrying, background = AuroraColors.NavigationSearchSurface,
                round = false, horizontalPadding = AuroraLayout.ChromeInset,
            ) {
                val label = if (error.retrying) Res.string.navigation_retrying_page
                else Res.string.navigation_retry_page
                val color = if (error.retrying) AuroraColors.Muted else AuroraColors.DesktopInk
                AuroraText(stringResource(label), color = color)
            }
            AuroraControl(
                {
                    val copied = try {
                        clipboard.setText(AnnotatedString(error.diagnostic))
                        clipboard.getText()?.text == error.diagnostic
                    } catch (_: Exception) {
                        false
                    }
                    copyStatus = if (copied) CopyStatus.Copied else CopyStatus.Failed
                },
                touch = touch, background = Color.Transparent, round = false, horizontalPadding = AuroraLayout.SmallGap,
            ) {
                val label = if (copyStatus == CopyStatus.Copied) Res.string.navigation_logs_copied
                else Res.string.navigation_copy_logs
                AuroraText(stringResource(label), color = AuroraColors.Primary)
            }
        }
        if (copyStatus == CopyStatus.Failed) {
            AuroraText(
                stringResource(Res.string.navigation_copy_failed),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

private enum class CopyStatus { Ready, Copied, Failed }
private const val CopyFeedbackMillis = 1600L
