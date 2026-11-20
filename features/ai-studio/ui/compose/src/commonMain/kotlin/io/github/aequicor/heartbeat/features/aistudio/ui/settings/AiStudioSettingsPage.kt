package io.github.aequicor.heartbeat.features.aistudio.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.components.HbSettingsRow
import io.github.aequicor.heartbeat.designsystem.components.HbSwitch
import io.github.aequicor.heartbeat.designsystem.foundation.HbPaneGeometry
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioSettings
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HbSpacing.S, vertical = HbSpacing.L),
    ) {
        Column(
            modifier = Modifier.widthIn(max = HbPaneGeometry.SettingsFormMax.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(HbSpacing.M),
        ) {
            HbSettingsRow(
                title = "Показывать аватар",
                description = "В боковой панели студии",
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
                    modifier = Modifier.padding(horizontal = HbSpacing.M),
                    style = HbTypography.Detail,
                    color = { colors.status.error.foreground },
                )
            }
        }
    }
}
