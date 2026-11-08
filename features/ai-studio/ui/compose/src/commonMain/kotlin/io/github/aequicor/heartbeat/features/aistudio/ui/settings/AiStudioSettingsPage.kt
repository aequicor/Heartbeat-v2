package io.github.aequicor.heartbeat.features.aistudio.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.designsystem.components.HbSettingsRow
import io.github.aequicor.heartbeat.designsystem.components.HbSwitch
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioSettings
import kotlinx.coroutines.launch

/**
 * AI studio settings page. The page owns its state and handlers: the avatar visibility is read from
 * and persisted through the shared [AiStudioSettings].
 */
@Composable
internal fun AiStudioSettingsPage(
    settings: AiStudioSettings,
    modifier: Modifier = Modifier,
) {
    val avatarVisible by settings.avatarVisible.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(HbSpacing.M),
    ) {
        HbSettingsRow(
            title = "Аватар пользователя",
            description = "Показывать аватар пользователя в навигации студии",
            control = { controlModifier ->
                HbSwitch(
                    label = "",
                    checked = avatarVisible,
                    onCheckedChange = { visible ->
                        scope.launch { settings.setAvatarVisible(visible) }
                    },
                    modifier = controlModifier,
                )
            },
        )
    }
}
