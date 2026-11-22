package io.github.aequicor.heartbeat.features.aistudio.presentation.state

import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.FileGroup
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Message
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.contract.AiStudioScreenState.User

internal class WorkspaceFiles {
    private var files = listOf<File>()
    private var groups = listOf<FileGroup>()

    fun find(id: String): File? = files.firstOrNull { it.id == id }

    fun groups(query: String): List<FileGroup> = groups.filterFiles(query)

    fun refresh(messages: List<Message>) {
        val next = files(messages)
        if (next != files) {
            files = next
            groups = prepareFileGroups(next)
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
    }.distinctBy(File::id)
}
