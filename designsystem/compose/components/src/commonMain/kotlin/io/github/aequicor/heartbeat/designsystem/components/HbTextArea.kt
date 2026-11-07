package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Modifier

/**
 * Multiline field for a message, a description or another growing text (HB-DS-016). The field
 * keeps the label, help and error contract of [HbTextField]; it starts with three lines and grows
 * with its content, and the owner constrains the height of the surrounding container when a cap is
 * needed.
 */
@Composable
@NonRestartableComposable
fun HbTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "",
    errorMessage: String? = null,
    enabled: Boolean = true,
) {
    HbTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        errorMessage = errorMessage,
        enabled = enabled,
        singleLine = false,
        minLines = 3,
        maxLines = Int.MAX_VALUE,
    )
}
