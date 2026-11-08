package io.github.aequicor.heartbeat.features.aistudio.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.designsystem.components.HbSettingsRow
import io.github.aequicor.heartbeat.designsystem.components.HbSwitch
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioSettings

/**
 * AI studio settings page. The page owns its state and handlers: the avatar visibility is read from
 * and written to the in-memory [AiStudioSettings] that the workspace shares.
 */
@Composable
internal fun AiStudioSettingsPage(
    settings: AiStudioSettings,
    modifier: Modifier = Modifier,
) {
    val avatarVisible by settings.avatarVisible.collectAsState()

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
                    onCheckedChange = settings::setAvatarVisible,
                    modifier = controlModifier,
                )
            },
        )
    }
}
