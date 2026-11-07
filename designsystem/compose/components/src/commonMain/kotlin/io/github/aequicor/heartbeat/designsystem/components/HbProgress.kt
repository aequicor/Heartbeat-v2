@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.foundation.HbMotions
import io.github.aequicor.heartbeat.designsystem.theme.HbSpacing
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults
import io.github.aequicor.heartbeat.designsystem.theme.HbTypography

/**
 * Progress of a bounded operation (HB-DS-026).
 *
 * [progress] is a fraction of a meaningful denominator; `null` renders an indeterminate bar,
 * because a spinner must not invent a percentage. The value is exposed through
 * `progressBarRangeInfo`, so the state is announced without reading the moving bar. The label names
 * the operation and stays outside the bar.
 */
@Composable
fun HbProgress(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    label: String? = null,
) {
    val colors = HbThemeDefaults.LocalColors.current
    val fraction = progress?.coerceIn(0f, 1f)
    val transition = rememberInfiniteTransition(label = "progress")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = HbMotions.Default.deliberateTransitionMs * IndeterminateCycles,
                easing = LinearEasing,
            ),
        ),
        label = "progressOffset",
    )

    Column(modifier = modifier) {
        if (label != null) {
            BasicText(
                text = label,
                style = HbTypography.Caption,
                color = { colors.text.secondary },
                modifier = Modifier.padding(bottom = HbSpacing.Xs),
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(TRACK_HEIGHT)
                .semantics {
                    progressBarRangeInfo = if (fraction == null) {
                        ProgressBarRangeInfo.Indeterminate
                    } else {
                        ProgressBarRangeInfo(fraction, 0f..1f)
                    }
                },
        ) {
            val corner = CornerRadius(size.height / 2f)
            drawRoundRect(color = colors.border.subtle, size = size, cornerRadius = corner)
            if (fraction != null) {
                drawRoundRect(
                    color = colors.accent.brand,
                    size = Size(size.width * fraction, size.height),
                    cornerRadius = corner,
                )
            } else {
                val segment = size.width * IndeterminateSegment
                drawRoundRect(
                    color = colors.accent.brand,
                    topLeft = Offset((size.width - segment) * offset, 0f),
                    size = Size(segment, size.height),
                    cornerRadius = corner,
                )
            }
        }
    }
}

private val TRACK_HEIGHT = 4.dp

private const val IndeterminateSegment = 0.3f

private const val IndeterminateCycles = 3
