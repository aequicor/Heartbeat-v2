package io.github.aequicor.heartbeat.features.aistudio.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.designsystem.components.HbSettingsRow
import io.github.aequicor.heartbeat.designsystem.components.HbSwitch
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.api.AiStudioSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * AI studio settings page. The page owns its state and handlers: the avatar visibility is read from
 * and persisted through the shared [AiStudioSettings]. The write runs in [appScope], so it is not
 * cancelled when the user leaves the page before DataStore commits.
 */
@Composable
internal fun AiStudioSettingsPage(
    settings: AiStudioSettings,
    appScope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val avatarVisible by settings.avatarVisible.collectAsState()
    val error by settings.error.collectAsState()
    val colors = HbTheme.colors

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
                        appScope.launch { settings.setAvatarVisible(visible) }
                    },
                    modifier = controlModifier,
                )
            },
        )
        val message = error
        if (message != null) {
            BasicText(
                text = message,
                style = HbTypography.Caption,
                color = { colors.status.error.foreground },
                modifier = Modifier.padding(top = HbSpacing.S),
            )
        }
    }
}
