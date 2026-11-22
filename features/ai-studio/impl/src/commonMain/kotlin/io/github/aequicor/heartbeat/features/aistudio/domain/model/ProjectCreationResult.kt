package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal sealed interface ProjectCreationResult {

    data class Registered(val project: Project.WithDirectory, val created: Boolean) : ProjectCreationResult

    data class NameConflict(val immutableName: String) : ProjectCreationResult

    data object InvalidWorkingDirectory : ProjectCreationResult
    data object InvalidName : ProjectCreationResult
}
