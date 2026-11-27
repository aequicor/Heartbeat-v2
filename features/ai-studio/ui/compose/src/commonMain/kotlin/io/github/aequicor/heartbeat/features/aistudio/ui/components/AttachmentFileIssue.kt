package io.github.aequicor.heartbeat.features.aistudio.ui.components

internal enum class AttachmentFileIssue(val label: String) {

    Missing("Файл удалён"),
    RemovedFromSession("Файл удалён из сессии"),
    Unavailable("Файл недоступен"),
    LoadFailed("Ошибка загрузки"),
}
