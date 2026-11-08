@file:Suppress("NoNullableLambdaWithDefaultNull")

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.aequicor.heartbeat.designsystem.foundation.HbEffects
import io.github.aequicor.heartbeat.designsystem.theme.HbSizes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbTheme
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Settings row: a label, optional explanation and the control of the setting (HB-DS-016).
 *
 * The title and the [description] stay visible and the [control] is a sibling, so a switch or a
 * select keeps its own accessible name and state. [onClick] makes the whole row an additional
 * target for hosts where clicking the row is expected; the inner control still handles its own
 * click first. The row grows with the text scale instead of clipping its explanation.
 *
 * [control] receives the modifier that carries the title as the accessible name. The caller must
 * apply that modifier to the interactive control itself: a nested control has its own semantics
 * node, so merging the row descendants cannot give the switch or the select its name. The modifier
 * is also the place for the caller's own styling.
 */
@Composable
fun HbSettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    control: @Composable (Modifier) -> Unit = {},
) {
    val colors = HbTheme.colors
    val titleColor = if (enabled) {
        colors.text.primary
    } else {
        colors.text.primary.copy(alpha = HbEffects.DisabledContent)
    }
    val descriptionColor = if (enabled) {
        colors.text.secondary
    } else {
        colors.text.secondary.copy(alpha = HbEffects.DisabledContent)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HbSizes.settingsRowHeight)
            .then(
                if (onClick != null && enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            // The label and the control form one accessible item: a switch without its own visible
            // label is announced with the row title instead of an anonymous toggle.
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = HbSpacing.M, vertical = HbSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HbSpacing.S),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(HbSpacing.Xxs),
        ) {
            BasicText(
                text = title,
                style = HbTypography.Label,
                color = { titleColor },
            )
            if (description != null) {
                BasicText(
                    text = description,
                    style = HbTypography.Caption,
                    color = { descriptionColor },
                )
            }
        }
        Box(modifier = Modifier.padding(end = HbSpacing.Xxs)) {
            control(
                Modifier.semantics {
                    if (title.isNotEmpty()) contentDescription = title
                },
            )
        }
    }
}
