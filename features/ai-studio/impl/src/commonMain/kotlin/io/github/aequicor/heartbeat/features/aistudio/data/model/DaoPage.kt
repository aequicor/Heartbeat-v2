package io.github.aequicor.heartbeat.features.aistudio.data.model

internal data class DaoPage<Item>(val items: List<Item>, val nextCursor: DaoPageCursor?)
