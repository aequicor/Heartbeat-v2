package io.github.aequicor.heartbeat.features.aistudio.data.dao

import io.github.aequicor.heartbeat.features.aistudio.data.model.DaoPage
import io.github.aequicor.heartbeat.features.aistudio.data.model.DaoPageCursor

internal fun <Item> studioPage(
    rows: List<Item>,
    limit: Int,
    cursor: (Item) -> DaoPageCursor,
): DaoPage<Item> = if (rows.size > limit) {
    DaoPage(rows.take(limit), cursor(rows[limit - 1]))
} else {
    DaoPage(rows, null)
}
