package io.github.aequicor.heartbeat.features.aistudio.ui.components

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

@Composable
@Suppress("InjectDispatcher") // The launcher owns the lifetime of the platform file write.
internal actual fun rememberArtifactSaveLauncher(onFinished: (failed: Boolean) -> Unit): (File) -> Unit {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<File?>(null) }
    val contract = remember { ActivityResultContracts.CreateDocument("application/octet-stream") }
    val launcher = rememberLauncherForActivityResult(contract) { uri ->
        val file = pending
        pending = null
        if (uri == null || file == null) {
            onFinished(false)
        } else {
            scope.launch {
                val failed = try {
                    withContext(Dispatchers.IO) {
                        requireNotNull(resolver.openOutputStream(uri)).use { it.write(file.originalBytes()) }
                    }
                    false
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    true
                }
                onFinished(failed)
            }
        }
    }
    return { file ->
        pending = file
        try {
            launcher.launch(file.name)
        } catch (_: ActivityNotFoundException) {
            pending = null
            onFinished(true)
        }
    }
}
