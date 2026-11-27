package io.github.aequicor.heartbeat.features.aistudio.data.workspace

import okio.FileSystem

internal actual fun attachmentFileSystem(): FileSystem = FileSystem.SYSTEM
