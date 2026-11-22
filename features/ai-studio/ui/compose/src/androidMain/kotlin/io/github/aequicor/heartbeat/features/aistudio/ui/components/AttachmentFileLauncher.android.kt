package io.github.aequicor.heartbeat.features.aistudio.ui.components

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberAttachmentFileLauncher(
    onResult: (reference: String?) -> Unit,
    onError: () -> Unit,
): () -> Unit {
    val contract = remember { ActivityResultContracts.OpenDocument() }
    val launcher = rememberLauncherForActivityResult(contract) { uri -> onResult(uri?.toString()) }
    return {
        try {
            launcher.launch(arrayOf("*/*"))
        } catch (_: ActivityNotFoundException) {
            onError()
        }
    }
}
