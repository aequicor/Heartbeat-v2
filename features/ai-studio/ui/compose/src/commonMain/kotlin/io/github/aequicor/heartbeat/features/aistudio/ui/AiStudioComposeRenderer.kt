package io.github.aequicor.heartbeat.features.aistudio.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioRoutes
import io.github.aequicor.heartbeat.features.aistudio.component.AiStudioComponent
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioAction
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.DraftAttachmentImageLoader
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioSettings
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioStore
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AttachmentFolderOpener
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AttachmentSourceInspector
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AuroraBackdrop
import io.github.aequicor.heartbeat.features.aistudio.ui.components.LocalAttachmentFolderOpener
import io.github.aequicor.heartbeat.features.aistudio.ui.components.LocalAttachmentSourceInspector
import io.github.aequicor.heartbeat.features.aistudio.ui.components.LocalDraftAttachmentImageLoader
import io.github.aequicor.heartbeat.features.aistudio.ui.components.loadAuroraBackdrop
import io.github.aequicor.heartbeat.navigation.compose.ComposeFeatureRenderer
import io.github.aequicor.heartbeat.navigation.decompose.FeatureRoot

/** Renders the AI studio workspace root; the shell resolves it by `featureId`. */
@ContributesIntoSet(AppScope::class)
class AiStudioComposeRenderer @Inject internal constructor(
    private val settings: AiStudioSettings,
    private val attachmentFolders: AttachmentFolderOpener,
    private val attachmentSourceInspector: AttachmentSourceInspector,
    private val attachmentImages: DraftAttachmentImageLoader,
) : ComposeFeatureRenderer {

    override val featureId: String = AiStudioRoutes.FEATURE_ID

    @Composable
    override fun Render(component: FeatureRoot) {
        require(component is AiStudioComponent && component is AiStudioStore) {
            "Unexpected AI studio component: ${component::class}"
        }

        val avatarVisible by settings.avatarVisible.collectAsState()
        val state by component.screenState.collectAsState()
        val backdrop by produceState<AuroraBackdrop?>(null) { value = loadAuroraBackdrop() }
        CompositionLocalProvider(
            LocalAttachmentFolderOpener provides attachmentFolders,
            LocalAttachmentSourceInspector provides attachmentSourceInspector,
            LocalDraftAttachmentImageLoader provides attachmentImages,
        ) {
            AiStudioScreen(
                state = state,
                draft = component.draft,
                liveText = component.liveText,
                backdrop = backdrop,
                projectsSessions = component.projectsSessions,
                onAction = { action ->
                    val projectsSessions = component.projectsSessions
                    when (action) {
                        is AiStudioAction.SearchChats -> projectsSessions.search(action.text)
                        AiStudioAction.ExpandChatSearch -> projectsSessions.setSearchVisible(true)
                        AiStudioAction.CollapseChatSearch -> projectsSessions.setSearchVisible(false)
                        AiStudioAction.ExpandChats -> projectsSessions.setPanelVisible(true)
                        AiStudioAction.CollapseChats -> projectsSessions.setPanelVisible(false)
                        else -> component.dispatch(action)
                    }
                },
                avatarVisible = avatarVisible,
                onOpenSettings = component::openSettings,
            )
        }
    }
}
