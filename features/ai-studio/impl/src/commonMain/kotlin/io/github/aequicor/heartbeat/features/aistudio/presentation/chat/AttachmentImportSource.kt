package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

/** Keeps the references available until [importFiles] returns. */
public fun interface AttachmentImportSource {

    @Throwable(Exception::class, CancellationException::class)
    public suspend fun withReferences(importFiles: suspend (List<String>) -> Unit)
}
