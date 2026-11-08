@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Disclosure section: a header that expands its content (HB-DS-019). The header is a button whose
 * expanded state is exposed through semantics, so assistive services announce the state and the
 * chevron is not the only indication.
 */
@Composable
fun HbDisclosure(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    stateDescription: String? = null,
    subtitle: String? = null,
    status: HbStatusTone? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = HbTheme.colors
    var focused by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(HbShapes.Small)
                .onFocusChanged { focused = it.isFocused }
                .hbFocusRing(focused = focused, shape = HbShapes.Small, color = colors.focus)
                .heightIn(min = HbSizes.minimumTarget)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = { onExpandedChange(!expanded) },
                )
                .disclosureSemantics(
                    enabled = enabled,
                    expanded = expanded,
                    stateDescription = stateDescription,
                    onExpandedChange = onExpandedChange,
                )
                .padding(horizontal = HbSpacing.S, vertical = HbSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
        ) {
            if (status != null) {
                HbStatusDot(tone = status)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(HbSpacing.Xxs),
            ) {
                BasicText(
                    text = title,
                    style = HbTypography.Label,
                    color = { colors.text.primary },
                )
                if (subtitle != null) {
                    BasicText(
                        text = subtitle,
                        style = HbTypography.Caption,
                        color = { colors.text.secondary },
                    )
                }
            }
            HbChevronGlyph(color = colors.text.secondary, expanded = expanded)
        }
        if (expanded) {
            Box(modifier = Modifier.padding(start = HbSpacing.S, top = HbSpacing.Xs)) {
                content()
            }
        }
    }
}

private fun Modifier.disclosureSemantics(
    enabled: Boolean,
    expanded: Boolean,
    stateDescription: String?,
    onExpandedChange: (Boolean) -> Unit,
): Modifier = semantics {
    if (enabled) {
        if (expanded) {
            collapse { onExpandedChange(false); true }
        } else {
            expand { onExpandedChange(true); true }
        }
    }
    stateDescription?.let { this.stateDescription = it }
}
