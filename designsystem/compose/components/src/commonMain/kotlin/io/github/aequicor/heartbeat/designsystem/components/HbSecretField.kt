package io.github.aequicor.heartbeat.designsystem.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Semantic wrapper over secure text input (HB-DS-017).
 *
 * The value is masked by a password transformation until the user reveals it; [revealLabel] and
 * [hideLabel] are the changing accessible names of that control and the visible text of the action,
 * so no glyph is used. The revealed state stays in the composition: a secret never enters automatic
 * UI restoration, logs or accessibility labels.
 *
 * The field owns the secure keyboard defaults: the password keyboard type and disabled
 * autocorrection, so the host does not suggest words of a secret. [keyboardOptions] and
 * [keyboardActions] let the owner add the platform's credential autofill and the submit action
 * without replacing the masking.
 */
@Composable
fun HbSecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    revealLabel: String,
    hideLabel: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Password,
        imeAction = ImeAction.Done,
        autoCorrectEnabled = false,
    ),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var revealed by remember { mutableStateOf(false) }
    val toggleLabel = if (revealed) hideLabel else revealLabel

    HbTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = if (revealed) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailing = { fieldEnabled ->
            if (value.isNotEmpty()) {
                HbLink(
                    text = toggleLabel,
                    onClick = { revealed = !revealed },
                    enabled = fieldEnabled,
                )
            }
        },
    )
}
