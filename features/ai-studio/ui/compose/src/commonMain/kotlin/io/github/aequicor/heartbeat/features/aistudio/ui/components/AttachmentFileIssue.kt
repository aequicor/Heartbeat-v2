package io.github.aequicor.heartbeat.features.aistudio.ui.components

internal enum class AttachmentFileIssue(val label: String) {

    Missing("Файл удалён"),
    RemovedFromCatalog("Файл убран из каталога"),
    Unavailable("Файл недоступен"),
    LoadFailed("Ошибка загрузки"),
}
