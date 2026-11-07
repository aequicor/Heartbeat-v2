package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Form field structure: persistent label, control and help or error text (HB-DS-016).
 *
 * The label stays visible, so the control is never identified only by a placeholder. The control
 * receives the same text as its accessible name, which is how the visible label and the editable
 * receives the same text as its accessible name, which is how the visible label and the editable
 * node stay associated without merging the trailing actions of the control. An error replaces the
 * help text, uses the error foreground role and is announced politely, so the state is not encoded
 * by color alone. The control keeps its own validation and error association.
 */
@Composable
fun HbFormField(
    label: String,
    modifier: Modifier = Modifier,
    helpText: String? = null,
    errorMessage: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = HbThemeDefaults.LocalColors.current
    val supportText = errorMessage ?: helpText

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(HbSpacing.Xs),
    ) {
        BasicText(
            text = label,
            style = HbTypography.Label,
            color = { colors.text.secondary },
        )
        content()
        if (supportText != null) {
            BasicText(
                text = supportText,
                style = HbTypography.Caption,
                color = {
                    if (errorMessage == null) {
                        colors.text.secondary
                    } else {
                        colors.status.error.foreground
                    }
                },
                modifier = Modifier.hbSupportTextSemantics(isError = errorMessage != null),
            )
        }
    }
}

private fun Modifier.hbSupportTextSemantics(isError: Boolean): Modifier =
    if (isError) {
        semantics { liveRegion = LiveRegionMode.Polite }
    } else {
        this
    }
