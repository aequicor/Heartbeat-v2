package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import kotlin.io.encoding.Base64

@Composable
internal expect fun rememberArtifactSaveLauncher(onFinished: (failed: Boolean) -> Unit): (File) -> Unit

internal fun File.originalBytes(): ByteArray =
    if (base64Encoded) Base64.decode(content) else content.encodeToByteArray()
