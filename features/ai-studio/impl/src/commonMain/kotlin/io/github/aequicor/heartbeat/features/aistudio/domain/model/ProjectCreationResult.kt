package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal sealed interface ProjectCreationResult {

    data class Created(val project: Project) : ProjectCreationResult

    data class NameAlreadyExists(val immutableName: String) : ProjectCreationResult
}
