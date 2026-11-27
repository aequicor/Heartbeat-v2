package io.github.aequicor.heartbeat.features.aistudio.ui.components.imageeditor

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

internal actual fun Modifier.imageEditorPlatformZoom(onZoom: (Float, Offset) -> Unit): Modifier = this
