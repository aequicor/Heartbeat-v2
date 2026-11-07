@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotions
import io.github.aequicor.heartbeat.designsystem.theme.HbShapes
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
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
    val colors = HbThemeDefaults.LocalColors.current
    var focused by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) ChevronExpandedDegrees else 0f,
        animationSpec = tween(durationMillis = HbMotions.Default.ordinaryTransitionMs),
        label = "disclosureChevron",
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(HbShapes.Small)
                .onFocusChanged { focused = it.isFocused }
                .hbFocusRing(focused = focused, shape = HbShapes.Small, color = colors.focus)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = { onExpandedChange(!expanded) },
                )
                .disclosureSemantics(
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
            DisclosureChevron(color = colors.text.secondary, rotation = rotation)
        }
        if (expanded) {
            Box(modifier = Modifier.padding(start = HbSpacing.S, top = HbSpacing.Xs)) {
                content()
            }
        }
    }
}

@Composable
private fun DisclosureChevron(
    color: Color,
    rotation: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(chevronSize).rotate(rotation)) {
        val stroke = size.minDimension * ChevronStrokeRatio
        drawLine(
            color = color,
            start = Offset(size.width * ChevronStartX, size.height * ChevronStartY),
            end = Offset(size.width * ChevronMiddleX, size.height * ChevronMiddleY),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * ChevronMiddleX, size.height * ChevronMiddleY),
            end = Offset(size.width * ChevronEndX, size.height * ChevronStartY),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

private val chevronSize = 12.dp

private const val ChevronStrokeRatio = 0.16f

private const val ChevronStartX = 0.2f

private const val ChevronStartY = 0.38f

private const val ChevronMiddleX = 0.5f

private const val ChevronMiddleY = 0.66f

private const val ChevronEndX = 0.8f

private const val ChevronExpandedDegrees = 180f

private fun Modifier.disclosureSemantics(
    expanded: Boolean,
    stateDescription: String?,
    onExpandedChange: (Boolean) -> Unit,
): Modifier = semantics {
    if (expanded) {
        collapse { onExpandedChange(false); true }
    } else {
        expand { onExpandedChange(true); true }
    }
    stateDescription?.let { this.stateDescription = it }
}
