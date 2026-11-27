package io.github.aequicor.heartbeat.features.aistudio.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File

@Composable
internal actual fun rememberAttachmentCopyLauncher(onFinished: (failed: Boolean) -> Unit): (File) -> Unit {
    val clipboard = LocalContext.current.getSystemService(ClipboardManager::class.java)
    val finished = rememberUpdatedState(onFinished)
    return { file ->
        val text = file.previewText()
        val failed = if (clipboard == null || text == null) true else try {
            clipboard.setPrimaryClip(ClipData.newPlainText(file.name, text))
            false
        } catch (_: SecurityException) {
            true
        }
        finished.value(failed)
    }
}
