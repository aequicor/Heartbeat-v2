package io.github.aequicor.heartbeat.features.aistudio.data.dao

import io.github.aequicor.heartbeat.core.database.DatabaseConnection

internal actual fun chatDraftOwnership(connection: DatabaseConnection): ChatDraftOwnership =
    UntrackedChatDraftOwnership
