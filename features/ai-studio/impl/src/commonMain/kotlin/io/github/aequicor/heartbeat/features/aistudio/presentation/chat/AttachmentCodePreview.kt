package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.ReplyCodeLine

/** Applies the same syntax grammar as code displayed in a chat reply. */
public fun File.previewCodeLines(text: String): List<ReplyCodeLine> =
    highlightReplyCode(text, if (isPatch) "diff" else name.substringAfterLast('.', "").lowercase())
