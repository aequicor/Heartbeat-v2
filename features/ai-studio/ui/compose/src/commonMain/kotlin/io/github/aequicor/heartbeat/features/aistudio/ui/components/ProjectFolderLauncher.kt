package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable

@Composable
internal expect fun rememberProjectFolderLauncher(
    onSelect: (path: String, name: String) -> Unit,
    onCancel: () -> Unit,
    onError: () -> Unit,
): () -> Unit

@Composable
internal fun rememberProjectFolderPicker(
    enabled: Boolean,
    onReading: (Boolean) -> Unit,
    onError: (Boolean) -> Unit,
    onSelectFolder: (path: String, name: String) -> Unit,
): () -> Unit {
    val launcher = rememberProjectFolderLauncher(
        onSelect = { path, name ->
            onSelectFolder(path, name)
            onReading(false)
        },
        onCancel = { onReading(false) },
        onError = {
            onReading(false)
            onError(true)
        },
    )
    return {
        if (enabled) {
            onError(false)
            onReading(true)
            launcher()
        }
    }
}
