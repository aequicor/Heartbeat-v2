package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal data class ProjectTree(
    val project: Project,
    val sessions: List<SessionTree>,
)

internal data class SessionTree(
    val session: Session,
    val children: List<SessionTree>,
)
