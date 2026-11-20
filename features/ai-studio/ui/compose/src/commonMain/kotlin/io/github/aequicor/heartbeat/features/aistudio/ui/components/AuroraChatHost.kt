package io.github.aequicor.heartbeat.features.aistudio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReusableContent

/** Resets chat-scoped state while allowing Compose to reuse the viewport's layout nodes. */
@Composable
internal fun AuroraChatHost(chatId: String, content: @Composable () -> Unit) {
    ReusableContent(chatId) { content() }
}
