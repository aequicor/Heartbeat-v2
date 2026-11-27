package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

public fun interface DraftAttachmentImageLoader {

    @Throwable(CancellationException::class)
    public suspend fun load(file: File): Result<File>
}
