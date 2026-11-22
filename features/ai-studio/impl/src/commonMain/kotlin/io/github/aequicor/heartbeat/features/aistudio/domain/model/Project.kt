package io.github.aequicor.heartbeat.features.aistudio.domain.model

import okio.Path
import kotlin.time.Instant

internal sealed interface Project {

    val id: ProjectId
    val title: String
    val status: ProjectStatus
    val isExpanded: Boolean

    data class WithoutDirectory(override val isExpanded: Boolean) : Project {
        override val id: ProjectId = ProjectId.Ungrouped
        override val title: String = UngroupedTitle
        override val status: ProjectStatus = ProjectStatus.ACTIVE
    }

    data class WithDirectory(
        override val id: ProjectId,
        val immutableName: String,
        override val title: String,
        val directory: ProjectDirectory,
        override val status: ProjectStatus,
        val createdAt: Instant,
        val updatedAt: Instant,
        override val isExpanded: Boolean,
    ) : Project {
        val workingDirectory: Path? get() = (directory as? ProjectDirectory.Local)?.path
    }

    companion object {
        const val UngroupedTitle = "Без проекта"
    }
}

internal sealed interface ProjectDirectory {
    data class Local(val path: Path) : ProjectDirectory
    data class DocumentTree(val uri: String) : ProjectDirectory
}
