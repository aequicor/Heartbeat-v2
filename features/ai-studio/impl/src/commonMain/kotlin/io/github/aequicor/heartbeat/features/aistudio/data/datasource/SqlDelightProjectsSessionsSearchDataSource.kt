package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.features.aistudio.data.dao.accessStudioDatabase
import io.github.aequicor.heartbeat.features.aistudio.data.model.DatabaseTimestamp
import io.github.aequicor.heartbeat.features.aistudio.data.model.ProjectDto
import io.github.aequicor.heartbeat.features.aistudio.data.model.SessionDto
import io.github.aequicor.heartbeat.features.aistudio.db.SearchProjectsSessions
import io.github.aequicor.heartbeat.features.aistudio.db.StudioSessionQueries
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ProjectsSessionsSearchDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectTree
import io.github.aequicor.heartbeat.features.aistudio.domain.model.Session
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionId
import io.github.aequicor.heartbeat.features.aistudio.domain.model.SessionTree

@Inject
internal class SqlDelightProjectsSessionsSearchDataSource(
    private val connection: DatabaseConnection,
) : ProjectsSessionsSearchDataSource {

    override suspend fun search(query: String): List<ProjectTree> {
        val pattern = query.toSearchPattern()
        val rows = accessStudioRepository {
            accessStudioDatabase("Search studio projects and sessions") {
                StudioSessionQueries(connection.driver).searchProjectsSessions(pattern).executeAsList()
            }
        }
        return rows.groupBy(SearchProjectsSessions::project_id).values.map(::toProjectTree)
    }
}

private fun toProjectTree(rows: List<SearchProjectsSessions>): ProjectTree {
    val project = rows.first().toProjectDto().toProject()
    val sessions = rows.mapNotNull(SearchProjectsSessions::toSessionOrNull)
    val sessionIds = sessions.mapTo(mutableSetOf(), Session::id)
    val branches = sessions.groupBy { it.parentSessionId?.takeIf(sessionIds::contains) }
    return ProjectTree(project, branches[null].orEmpty().map { it.toTree(branches) })
}

private fun Session.toTree(branches: Map<SessionId?, List<Session>>): SessionTree =
    SessionTree(this, branches[id].orEmpty().map { it.toTree(branches) })

private fun SearchProjectsSessions.toProjectDto() = ProjectDto(
    id = project_id,
    immutableName = project_name,
    title = project_title,
    workingDirectory = working_directory,
    status = project_status,
    createdAt = DatabaseTimestamp(project_created_seconds, project_created_nanos),
    updatedAt = DatabaseTimestamp(project_updated_seconds, project_updated_nanos),
    isExpanded = project_expanded == 1L,
)

private fun SearchProjectsSessions.toSessionOrNull(): Session? {
    val id = session_id ?: return null
    return SessionDto(
        id = id,
        projectId = project_id,
        title = checkNotNull(session_title),
        status = checkNotNull(session_status),
        createdAt = DatabaseTimestamp(checkNotNull(session_created_seconds), checkNotNull(session_created_nanos)),
        updatedAt = DatabaseTimestamp(checkNotNull(session_updated_seconds), checkNotNull(session_updated_nanos)),
        parentSessionId = parent_session_id,
        isExpanded = session_expanded == 1L,
        workState = checkNotNull(session_work_state),
    ).toSession()
}

// SQLite GLOB is case-sensitive. Explicit character classes keep the same matching across drivers.
private val caseVariants by lazy {
    (Char.MIN_VALUE..Char.MAX_VALUE)
        .filter { it.uppercaseChar() != it || it.lowercaseChar() != it }
        .groupBy { it.uppercaseChar().lowercaseChar() }
        .mapValues { (key, variants) -> (variants + key).distinct().joinToString(separator = "") }
}

private fun String.toSearchPattern(): String = buildString {
    append('*')
    for (character in this@toSearchPattern) {
        val variants = caseVariants[character.uppercaseChar().lowercaseChar()]
        when {
            variants != null -> append('[').append(variants).append(']')
            character == '*' || character == '?' || character == '[' -> append('[').append(character).append(']')
            else -> append(character)
        }
    }
    append('*')
}
