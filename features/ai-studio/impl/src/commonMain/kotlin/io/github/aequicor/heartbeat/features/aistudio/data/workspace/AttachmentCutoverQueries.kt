package io.github.aequicor.heartbeat.features.aistudio.data.workspace

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File

/** Queries used only in the schema-upgrade transaction, while the old columns still exist. */
internal class AttachmentCutoverQueries(private val driver: SqlDriver) {

    fun claim(version: Long): Boolean =
        driver.execute(null, "DELETE FROM attachment_migration_pending WHERE version = ?", 1) {
            bindLong(0, version)
        }.value == 1L

    fun nextDraft(after: String?): Draft? = first(
        """
        SELECT id, project_id, workspace_payload, draft_revision FROM studio_session
        WHERE (? IS NULL OR id > ?) AND (workspace_payload IS NOT NULL OR draft_revision IS NOT NULL)
        ORDER BY id LIMIT 1
        """, after, after,
    ) {
        Draft(checkNotNull(it.getString(0)), checkNotNull(it.getString(1)), it.getString(2), it.getString(3))
    }

    fun retireDraft(id: String, payload: String?) = execute(
        "UPDATE studio_session SET workspace_payload = ?, draft_revision = NULL WHERE id = ?", payload, id,
    )

    fun insertDraftSource(id: String, file: File, ref: String, presentation: String) {
        check(driver.execute(null, """
            INSERT INTO attachment(id, name, mime_type, size_bytes, storage_ref, presentation)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent(), 6) {
            bindString(0, id)
            bindString(1, file.name)
            bindString(2, file.mimeType ?: file.image?.mimeType)
            bindLong(3, file.sourceSizeBytes ?: 0L)
            bindString(4, ref)
            bindString(5, presentation)
        }.value == 1L)
    }

    fun nextInlineAttachment(after: String?): InlineAttachment? = first(
        """
        SELECT a.id, a.name, a.mime_type, a.size_bytes, a.storage_ref, a.presentation, a.inline_text,
            EXISTS (SELECT 1 FROM message_part WHERE attachment_id = a.id),
            EXISTS (SELECT 1 FROM sent_attachment WHERE attachment_id = a.id)
        FROM attachment a
        WHERE (? IS NULL OR a.id > ?)
            AND (a.inline_text IS NOT NULL OR EXISTS (SELECT 1 FROM message_part WHERE attachment_id = a.id))
        ORDER BY a.id LIMIT 1
        """, after, after,
    ) {
        InlineAttachment(
            checkNotNull(it.getString(0)), checkNotNull(it.getString(1)), it.getString(2),
            checkNotNull(it.getLong(3)), checkNotNull(it.getString(4)), it.getString(5), it.getString(6),
            it.getLong(7) == 1L, it.getLong(8) == 1L,
        )
    }

    fun owningSession(attachmentId: String): Owner? = first(
        """
        SELECT session.id, session.project_id
        FROM studio_session session
        JOIN chat c ON c.session_id = session.id
        JOIN chat_entry e ON e.chat_id = c.id
        WHERE e.message_id IN (
            SELECT r.message_id FROM message_part p JOIN message_revision r ON r.revision_id = p.revision_id
            WHERE p.attachment_id = ?
            UNION
            SELECT r.message_id FROM sent_attachment s
            JOIN message_part p ON p.sent_attachment_id = s.id JOIN message_revision r ON r.revision_id = p.revision_id
            WHERE s.attachment_id = ?
        )
        ORDER BY session.id LIMIT 1
        """, attachmentId, attachmentId,
    ) { Owner(checkNotNull(it.getString(0)), checkNotNull(it.getString(1))) }

    fun replaceInlineReferences(attachmentId: String, snapshotId: String) = execute(
        "UPDATE message_part SET attachment_id = NULL, sent_attachment_id = ? WHERE attachment_id = ?",
        snapshotId, attachmentId,
    )

    fun retireInlineAttachment(id: String, ref: String, presentation: String) = execute(
        "UPDATE attachment SET inline_text = NULL, storage_ref = ?, presentation = ? WHERE id = ?",
        ref, presentation, id,
    )

    fun nextSource(after: String?): Source? = first(
        """
        SELECT id, storage_ref, presentation FROM attachment
        WHERE (? IS NULL OR id > ?) ORDER BY id LIMIT 1
        """, after, after,
    ) { Source(checkNotNull(it.getString(0)), checkNotNull(it.getString(1)), it.getString(2)) }

    fun normalizeSource(id: String, original: String?, managed: String?, presentation: String?) = execute(
        "UPDATE attachment SET original_uri = ?, managed_path = ?, presentation = ? WHERE id = ?",
        original, managed, presentation, id,
    )

    private fun execute(sql: String, vararg arguments: String?): Long =
        driver.execute(null, sql, arguments.size) {
            arguments.forEachIndexed { index, value -> bindString(index, value) }
        }.value

    private fun <T : Any> first(sql: String, vararg arguments: String?, map: (SqlCursor) -> T): T? =
        driver.executeQuery(null, sql.trimIndent(), { cursor ->
            QueryResult.Value(if (cursor.next().value) map(cursor) else null)
        }, arguments.size) {
            arguments.forEachIndexed { index, value -> bindString(index, value) }
        }.value

    data class Draft(val id: String, val projectId: String, val payload: String?, val revision: String?)

    data class InlineAttachment(
        val id: String,
        val name: String,
        val mimeType: String?,
        val size: Long,
        val storageRef: String,
        val presentation: String?,
        val content: String?,
        val hasDirectParts: Boolean,
        val hasSnapshots: Boolean,
    )

    data class Owner(val sessionId: String, val projectId: String)

    data class Source(val id: String, val storageRef: String, val presentation: String?)
}
