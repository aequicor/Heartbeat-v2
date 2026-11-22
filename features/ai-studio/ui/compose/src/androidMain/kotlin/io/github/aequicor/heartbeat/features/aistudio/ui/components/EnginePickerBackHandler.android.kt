package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler

@Composable
internal actual fun EnginePickerBackHandler(onBack: () -> Unit) = BackHandler(onBack = onBack)
