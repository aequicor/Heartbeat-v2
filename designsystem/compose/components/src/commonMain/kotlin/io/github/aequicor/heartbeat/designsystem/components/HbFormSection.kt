package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeTokens
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Group of related form fields under one section title (HB-DS-016). The section owns spacing
 * between fields; each field keeps its own label, help and error.
 */
@Composable
fun HbFormSection(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = HbThemeTokens.colors

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs)) {
            BasicText(
                text = title,
                style = HbTypography.Title,
                color = { colors.text.primary },
            )
            if (description != null) {
                BasicText(
                    text = description,
                    style = HbTypography.Caption,
                    color = { colors.text.secondary },
                )
            }
        }
        content()
    }
}
