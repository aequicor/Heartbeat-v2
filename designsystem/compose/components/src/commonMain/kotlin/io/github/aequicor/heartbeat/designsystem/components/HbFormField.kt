package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Form field structure: persistent label, control and help or error text (HB-DS-016).
 *
 * The label stays visible, so the control is never identified only by a placeholder. The label and
 * the error are provided to the control through composition locals, so a control without its own
 * label still gets a programmatic association instead of relying on visual proximity. An error
 * replaces the help text, uses the error foreground role and is announced politely, so the state is
 * not encoded by color alone.
 */
@Composable
fun HbFormField(
    label: String,
    modifier: Modifier = Modifier,
    helpText: String? = null,
    errorMessage: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = HbTheme.colors
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
        CompositionLocalProvider(
            HbFormFieldLocals.LocalLabel provides label,
            HbFormFieldLocals.LocalError provides errorMessage,
            content = content,
        )
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
