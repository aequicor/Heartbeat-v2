package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
                    HbCloseGlyph(color = colors.text.secondary)
                }
            }
        },
    )
}
