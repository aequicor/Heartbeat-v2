package io.github.aequicor.heartbeat.features.aistudio.domain.model

import kotlinx.serialization.Serializable

internal interface WorkspaceChat {
    val id: String
    val title: String
    val project: String
}

/** Owns chat order, selection and archive independently of the chat content. */
@Serializable
internal data class ChatWorkspace<C : WorkspaceChat>(
    val selectedId: String,
    val chats: List<C>,
    val serial: Long = 0,
    val collapsedNavigation: Set<String> = setOf(),
    val expandedProjects: Set<String> = setOf(),
    val archivedChats: Set<String> = setOf(),
) {
    fun findChat(id: String): C? = chats.firstOrNull { it.id == id }

    fun select(id: String): ChatWorkspace<C> =
        if (findChat(id) == null) this else copy(selectedId = id)

    fun addChat(chat: C): ChatWorkspace<C> {
        require(findChat(chat.id) == null)
        val index = chats.indexOfFirst { it.project == chat.project }.coerceAtLeast(0)
        val ordered = chats.toMutableList()
        ordered.add(index, chat)
        return copy(chats = ordered)
    }

    fun archive(id: String): ChatWorkspace<C> =
        if (id in archivedChats || findChat(id) == null) this else copy(archivedChats = archivedChats + id)

    fun activeChats(): Sequence<C> = chats.asSequence().filterNot { it.id in archivedChats }

    fun updateChat(id: String, update: (C) -> C): ChatWorkspace<C> =
        copy(chats = chats.map { if (it.id == id) update(it) else it })

    fun recover(recoverChat: (C) -> C): ChatWorkspace<C> {
        val selected = findChat(selectedId) ?: activeChats().firstOrNull() ?: chats.firstOrNull()
        return copy(selectedId = selected?.id.orEmpty(), chats = chats.map(recoverChat))
    }

    fun advanceSerial(): ChatWorkspace<C> = copy(serial = serial + 1)

    fun generatedId(): String = "aurora-$serial"
}
