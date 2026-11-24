package io.github.aequicor.heartbeat.features.aistudio.data.dao

import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import kotlin.uuid.Uuid

internal interface ChatDraftOwnership {
    fun claim(): Uuid?
    fun ended(owner: Uuid): Boolean
}

internal expect fun chatDraftOwnership(connection: DatabaseConnection): ChatDraftOwnership

internal object UntrackedChatDraftOwnership : ChatDraftOwnership {
    override fun claim(): Uuid? = null
    override fun ended(owner: Uuid): Boolean = false
}
