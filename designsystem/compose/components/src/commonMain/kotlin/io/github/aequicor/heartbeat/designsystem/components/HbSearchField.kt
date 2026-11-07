@file:Suppress("MagicNumber")

package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import io.github.aequicor.heartbeat.designsystem.theme.HbThemeDefaults

/**
 * Search field with an explicit clear action (HB-DS-016).
 *
 * The field keeps the label of [HbTextField], so the accessible name is present even when the query
 * is empty. [clearDescription] names the clear action, such as "Clear search". Result counts and
 * the no-results state belong to the owner of the search, not to the field.
 */
@Composable
fun HbSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    clearDescription: String,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "",
    enabled: Boolean = true,
) {
    val colors = HbThemeDefaults.LocalColors.current

    HbTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        enabled = enabled,
        trailing = {
            if (value.isNotEmpty()) {
                HbIconButton(
                    contentDescription = clearDescription,
                    onClick = { onValueChange("") },
                    enabled = enabled,
                ) {
                    ClearGlyph(color = colors.text.secondary)
                }
            }
        },
    )
}

@Composable
private fun ClearGlyph(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(CLEAR_GLYPH_SIZE)) {
        val stroke = size.minDimension * ClearStrokeRatio
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width, 0f),
            end = Offset(0f, size.height),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

private val CLEAR_GLYPH_SIZE = 10.dp

private const val ClearStrokeRatio = 0.16f
