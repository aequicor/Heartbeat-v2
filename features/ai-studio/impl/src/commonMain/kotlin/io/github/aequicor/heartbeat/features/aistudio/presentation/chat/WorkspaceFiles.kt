package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.FileGroup
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Message
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.User

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class WorkspaceFiles {

    private var stored = listOf<File>()
    private var files = listOf<File>()
    private var groups = listOf<FileGroup>()

    fun find(id: String): File? = files.firstOrNull { it.openId == id }

    fun setStored(headers: List<File>) {
        stored = headers
    }

    suspend fun toggleFiles(
        screen: MutableStateFlow<AiStudioScreenState>,
        visible: Boolean,
        storage: StudioWorkspaceStorage?,
    ) {
        val id = screen.value.chat?.id
        if (visible && id != null && storage != null) {
            stored = screen.readStored { storage.listFiles(id) } ?: return
            refresh(screen.value.messages)
        }
        screen.update { it.copy(filesVisible = visible, fileGroups = groups(it.fileSearch)) }
    }

    fun groups(query: String): List<FileGroup> = groups.filterFiles(query)

    fun refresh(messages: List<Message>) {
        val next = (files(messages) + stored).distinctBy(File::openId)
        if (next != files) {
            files = next
            groups = prepareFileGroups(next.distinctBy(File::id))
        }
    }

    fun search(screen: AiStudioScreenState, query: String): AiStudioScreenState =
        if (screen.filesVisible && screen.fileSearchVisible) {
            screen.copy(fileSearch = query, fileGroups = groups(query))
        } else screen

    fun setSearchVisible(screen: AiStudioScreenState, visible: Boolean): AiStudioScreenState = when {
        !screen.filesVisible -> screen
        visible -> screen.copy(fileSearchVisible = true)
        else -> screen.copy(fileSearchVisible = false, fileSearch = "", fileGroups = groups)
    }

    private fun files(messages: List<Message>): List<File> = buildList {
        messages.forEach { message ->
            when (message) {
                is User -> addAll(message.files)
                is Reply -> message.result?.let(::add)
            }
        }
    }.distinctBy(File::openId)
}
