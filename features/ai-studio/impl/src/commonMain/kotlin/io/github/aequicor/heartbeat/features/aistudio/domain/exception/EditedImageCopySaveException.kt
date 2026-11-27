package io.github.aequicor.heartbeat.features.aistudio.domain.exception

internal class EditedImageCopySaveException(val busy: Boolean, cause: Throwable) : Exception(cause)
