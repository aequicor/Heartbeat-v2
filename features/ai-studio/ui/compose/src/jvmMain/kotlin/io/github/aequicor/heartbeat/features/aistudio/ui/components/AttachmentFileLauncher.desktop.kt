package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.path

@Composable
internal actual fun rememberAttachmentFileLauncher(
    onResult: (reference: String?) -> Unit,
    onError: () -> Unit,
): () -> Unit {
    val launcher = rememberFilePickerLauncher(onError = { onError() }) { file ->
        onResult(file?.path)
    }
    return { launcher.launch() }
}
