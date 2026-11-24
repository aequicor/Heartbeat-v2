package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

@Composable
internal actual fun rememberArtifactSaveLauncher(onFinished: (failed: Boolean) -> Unit): (File) -> Unit {
    val scope = rememberCoroutineScope()
    val finished = rememberUpdatedState(onFinished)
    return { file ->
        scope.launch {
            val failed = try {
                val extension = file.name.substringAfterLast('.', "").takeUnless { it.isEmpty() }
                val name = if (extension == null) file.name else file.name.substringBeforeLast('.')
                FileKit.openFileSaver(suggestedName = name, defaultExtension = extension)?.write(file.originalBytes())
                false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                true
            }
            finished.value(failed)
        }
    }
}
