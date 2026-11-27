package io.github.aequicor.heartbeat.features.aiengine.api.session

import kotlinx.coroutines.flow.StateFlow

/** Child conversations exposed on the parent session's connection. */
public interface SessionSubagents {

    public val subagents: StateFlow<List<Subagent>>

    public data class Subagent(
        val id: String,
        val parentId: String,
        val title: String?,
        val description: String?,
        val activity: Activity,
        val items: List<SessionTimelineItem>,
    )

    public enum class Activity { Unknown, Running, RequiresAction, Idle }
}
