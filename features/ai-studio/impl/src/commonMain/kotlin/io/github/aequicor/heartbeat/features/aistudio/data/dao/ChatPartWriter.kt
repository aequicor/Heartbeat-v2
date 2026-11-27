package io.github.aequicor.heartbeat.features.aistudio.data.dao

import io.github.aequicor.heartbeat.features.aistudio.db.ChatQueries
import io.github.aequicor.heartbeat.features.aistudio.db.Message_part

internal fun ChatQueries.syncPart(remaining: ArrayDeque<Message_part>, part: Message_part): Long {
    val old = remaining.removeFirstOrNull()
    if (old != null && old.matches(part)) {
        if (old != part.copy(id = old.id)) {
            updatePart(part.title, part.status, part.body, part.presentation, part.sent_attachment_id, old.id)
        }
        return old.id
    }
    if (old != null) {
        deletePartsFrom(part.revision_id, old.position)
        remaining.clear()
    }
    insertPart(
        part.revision_id, part.parent_part_id, part.position, part.kind, part.title, part.status, part.body,
        part.tool_call_id, part.presentation, part.sent_attachment_id,
    )
    return lastInsertedId().executeAsOne()
}

private fun Message_part.matches(part: Message_part): Boolean =
    kind == part.kind && parent_part_id == part.parent_part_id && tool_call_id == part.tool_call_id
