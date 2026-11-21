package io.github.aequicor.heartbeat.features.aistudio.data

import app.cash.sqldelight.coroutines.asFlow
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionKey
import io.github.aequicor.heartbeat.features.aistudio.db.AllSessions
import io.github.aequicor.heartbeat.features.aistudio.db.ChatQueries
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid
import okio.Path.Companion.toPath

internal class StudioCatalog(connection: DatabaseConnection) {
    private val queries by lazy { ChatQueries(connection.driver) }
    fun changes() = queries.catalogChanges().asFlow()

    fun openProject(directory: String, name: String): String = queries.transactionWithResult {
        val path = directory.toPath(normalize = true)
        require(path.isAbsolute && name.isNotBlank()) { "Укажите имя проекта и абсолютный путь к папке" }
        val cwd = path.toString()
        queries.addProject(Uuid.random().toString(), name, cwd)
        queries.projectByDirectory(cwd).executeAsOne()
    }

    data class Rows(val projects: Map<String, String>, val chats: List<AllSessions>)

    fun read(): Rows = queries.transactionWithResult {
        val projects = queries.allProjects().executeAsList().associate { it.id to it.name }
        Rows(projects, queries.allSessions().executeAsList())
    }

    fun directory(projectId: String): String? = queries.projectDirectory(projectId).executeAsOneOrNull()?.working_directory

    fun transaction(block: () -> Unit) = queries.transaction { block() }

    fun importLocal(id: String, title: String, project: String?, details: String, archived: Boolean, parent: String?) {
        if (project != null) queries.addProject(project, project, null)
        queries.addSession(id, title, project, null, null, 0, details, parent)
        if (archived) queries.archive(id)
    }

    fun createLocal(projectId: String?): String {
        val id = Uuid.random().toString()
        require(projectId == null || queries.projectDirectory(projectId).executeAsOneOrNull() != null)
        queries.addSession(id, "Новый диалог", projectId, null, null, now(), null, null)
        return id
    }

    fun recordNative(projectId: String, key: SessionKey, created: Boolean = false): String =
        queries.transactionWithResult {
            val activity = key.updatedAt?.let(Instant::parseOrNull)?.toEpochMilliseconds() ?: if (created) now() else 0
            val local = queries.findNative(key.engine.id, key.id).executeAsOneOrNull()
            if (local != null) {
                queries.updateNative(activity, key.title, key.engine.id, key.id)
                local
            } else {
                val id = Uuid.random().toString()
                queries.addSession(id, key.title ?: "Новый диалог", projectId, key.engine.id, key.id, activity, null, null)
                id
            }
        }

    fun recordPage(projectId: String, keys: List<SessionKey>) = queries.transaction {
        keys.forEach { recordNative(projectId, it) }
    }

    fun recordActivity(id: String, title: String) = queries.updateActivity(title, now(), id)

    fun archive(id: String) = queries.archive(id)

    fun details(id: String): String? = queries.sessionDetails(id).executeAsOneOrNull()?.details

    fun saveDetails(id: String, details: String) = queries.updateDetails(details, id)

    private fun now() = Clock.System.now().toEpochMilliseconds()
}
