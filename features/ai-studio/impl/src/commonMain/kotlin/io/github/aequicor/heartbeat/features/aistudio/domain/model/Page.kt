package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal data class Page<Item, Id>(
    val items: List<Item>,
    val nextCursor: PageCursor<Id>?,
)
