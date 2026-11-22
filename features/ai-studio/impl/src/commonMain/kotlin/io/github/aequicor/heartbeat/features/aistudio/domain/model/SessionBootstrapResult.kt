package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal sealed interface SessionBootstrapResult {
    data class Created(
        val session: Session,
        val project: Project,
    ) : SessionBootstrapResult

    data object ProjectUnavailable : SessionBootstrapResult
}

