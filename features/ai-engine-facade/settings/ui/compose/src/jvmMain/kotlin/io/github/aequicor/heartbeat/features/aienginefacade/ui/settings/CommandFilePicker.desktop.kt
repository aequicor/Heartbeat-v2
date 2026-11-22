package io.github.aequicor.heartbeat.features.aienginefacade.ui.settings

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.path

@Composable
internal fun rememberCommandFilePicker(onSelect: (String) -> Unit, onError: () -> Unit): () -> Unit {
    val launcher = rememberFilePickerLauncher(onError = { onError() }) { file ->
        file?.let { onSelect(it.path) }
    }
    return { launcher.launch() }
}
