package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.EditedImageKindPrefix
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.FileDate
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.FileGroup
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.FileRow
import kotlin.math.roundToLong

private val months = listOf(
    "Январь" to "янв", "Февраль" to "фев", "Март" to "мар", "Апрель" to "апр",
    "Май" to "мая", "Июнь" to "июн", "Июль" to "июл", "Август" to "авг",
    "Сентябрь" to "сен", "Октябрь" to "окт", "Ноябрь" to "ноя", "Декабрь" to "дек",
)
private const val Kibibyte = 1_024
private const val Mebibyte = Kibibyte * Kibibyte

internal fun prepareFileGroups(files: List<File>): List<FileGroup> {
    val dated = files.map { file -> file to file.date?.takeIf { it.month in 1..months.size } }
    val groups = dated.sortedWith(
        compareByDescending<Pair<File, FileDate?>> { it.second?.year }
            .thenByDescending { it.second?.month }
            .thenByDescending { it.second?.day }
            .thenByDescending { it.second?.time },
    ).groupBy { (_, date) -> date?.let { it.year to it.month } }
    val multipleYears = groups.keys.mapNotNull { it?.first }.distinct().size > 1
    return groups.map { (month, entries) ->
        val key = month?.let { (year, number) -> "month:$year:$number" } ?: "undated"
        val title = month?.let { (year, number) ->
            val name = months[number - 1].first
            if (multipleYears) "$name $year" else name
        } ?: "Без даты"
        FileGroup(key, title, entries.map { (file, date) -> file.toRow(date) })
    }
}

private fun File.toRow(date: FileDate?): FileRow {
    val dot = name.lastIndexOf('.')
    val extension = if (dot > 0 && dot < name.lastIndex) name.substring(dot + 1).lowercase() else null
    val dateLabel = date?.let { "${it.day} ${months[it.month - 1].second}, ${it.time}" }
    val media = image != null || when (mimeType) {
        null, "application/octet-stream" -> extension in MediaExtensions
        else -> mimeType.substringBefore('/') in MediaMimeTypes
    }
    val metadata = kind.takeIf { catalogUri == "edited-image:$id" && it.startsWith(EditedImageKindPrefix) } ?: sizeLabel
    return FileRow(
        openId, name, extension, metadata, dateLabel, image, media, originalUri, managedCopyPath,
        canRemove = storedAttachmentId != null,
    )
}

internal fun formatAttachmentSize(bytes: Long): String = when {
    bytes < Kibibyte -> "$bytes Б"
    bytes < Mebibyte -> "${fractionalSize(bytes.toDouble() / Kibibyte)} КБ"
    else -> "${fractionalSize(bytes.toDouble() / Mebibyte)} МБ"
}

private fun fractionalSize(value: Double): String {
    val tenths = (value * 10).roundToLong()
    val integer = tenths / 10
    val fractional = tenths % 10
    return if (fractional == 0L) "$integer" else "$integer,$fractional"
}

private val MediaMimeTypes = setOf("image", "audio", "video")
private val MediaExtensions = setOf("png", "jpg", "jpeg", "webp", "gif", "mp3", "m4a", "wav", "ogg", "flac", "mp4", "mov", "webm", "mkv")

internal fun List<FileGroup>.filterFiles(query: String): List<FileGroup> {
    if (query.isEmpty()) return this
    return mapNotNull { group ->
        val matches = group.files.filter { it.name.contains(query, ignoreCase = true) }
        if (matches.isEmpty()) null else group.copy(files = matches)
    }
}
