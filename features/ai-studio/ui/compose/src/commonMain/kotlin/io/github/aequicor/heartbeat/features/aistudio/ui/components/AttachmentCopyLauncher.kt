package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File

@Composable
internal expect fun rememberAttachmentCopyLauncher(onFinished: (failed: Boolean) -> Unit): (File) -> Unit
