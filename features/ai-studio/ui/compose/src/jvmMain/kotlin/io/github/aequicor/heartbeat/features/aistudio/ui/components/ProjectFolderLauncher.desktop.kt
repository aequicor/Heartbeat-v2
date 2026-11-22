package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.isDirectory
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal actual fun rememberProjectFolderLauncher(
    onSelect: (path: String, name: String) -> Unit,
    onCancel: () -> Unit,
    onError: () -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher = rememberDirectoryPickerLauncher(onError = { onError() }) { directory ->
        if (directory == null) onCancel()
        else scope.launch {
            try {
                if (directory.isDirectory()) onSelect(directory.path, directory.name) else onError()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                onError()
            }
        }
    }
    return { launcher.launch() }
}
