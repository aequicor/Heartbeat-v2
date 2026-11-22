package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Chat
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.DeletionError
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.NavigationKind
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.NavigationRow
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.RenameError
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.Res
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.more_sessions
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.more_projects
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.new_session
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.project_deletion_failed
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.project_rename_failed
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.session_deletion_failed
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.session_rename_failed
import io.github.aequicor.heartbeat.features.aistudio.ui.resources.ungrouped_sessions
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AiStudioScreenState.navigationErrorText(): String? =
    renameErrorText(renameError) ?: deletionErrorText(deletionError) ?: storageError

@Composable
private fun renameErrorText(error: RenameError?): String? = when (error) {
    RenameError.Project -> stringResource(Res.string.project_rename_failed)
    RenameError.Session -> stringResource(Res.string.session_rename_failed)
    null -> null
}

@Composable
private fun deletionErrorText(error: DeletionError?): String? = when (error) {
    DeletionError.Project -> stringResource(Res.string.project_deletion_failed)
    DeletionError.Session -> stringResource(Res.string.session_deletion_failed)
    null -> null
}

@Composable
internal fun Chat.displayTitle(): String = if (isUntitled) stringResource(Res.string.new_session) else title

@Composable
internal fun Chat.displayProject(): String = if (isUngrouped) stringResource(Res.string.ungrouped_sessions) else project

@Composable
internal fun NavigationRow.displayTitle(): String = when (kind) {
    NavigationKind.More, NavigationKind.MoreChildren -> stringResource(Res.string.more_sessions)
    NavigationKind.MoreProjects -> stringResource(Res.string.more_projects)
    NavigationKind.Section -> stringResource(Res.string.ungrouped_sessions)
    NavigationKind.Chat -> if (isUntitled) stringResource(Res.string.new_session) else title
    NavigationKind.Project -> title
}

internal val NavigationKind.isPagination: Boolean
    get() = this == NavigationKind.More || this == NavigationKind.MoreProjects || this == NavigationKind.MoreChildren
