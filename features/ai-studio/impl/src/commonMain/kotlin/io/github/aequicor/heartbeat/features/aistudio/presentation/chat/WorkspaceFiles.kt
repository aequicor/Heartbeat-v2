package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.File
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.FileDate
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.FileGroup
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Message
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.Reply
import io.github.aequicor.heartbeat.features.aistudio.presentation.chat.AiStudioScreenState.User
import io.github.aequicor.heartbeat.features.aistudio.domain.exception.StudioRepositoryException

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class WorkspaceFiles {

    private var stored: List<File>? = null
    private var projectCatalog = false
    private val removedFileIds = mutableSetOf<String>()
    private val removedFileKeys = mutableSetOf<String>()
    private var files = listOf<File>()
    private var listedFiles = listOf<File>()
    private var groups = listOf<FileGroup>()

    fun find(id: String): File? = files.firstOrNull { it.openId == id }

    fun selectSession() {
        stored = null
        projectCatalog = false
        removedFileIds.clear()
        removedFileKeys.clear()
    }

    suspend fun toggleFiles(
        screen: MutableStateFlow<AiStudioScreenState>,
        visible: Boolean,
        storage: StudioWorkspaceStorage?,
    ) {
        if (!visible) {
            screen.update { it.copy(filesVisible = false) }
            return
        }
        refreshCatalog(screen, storage, visible = true)
    }

    suspend fun refreshCatalog(
        screen: MutableStateFlow<AiStudioScreenState>,
        storage: StudioWorkspaceStorage?,
        visible: Boolean = screen.value.filesVisible,
    ) {
        val id = screen.value.chat?.id
        if (id != null && storage != null) {
            val loaded = screen.readStored { storage.listFiles(id) } ?: return
            if (screen.value.chat?.id != id) return
            stored = loaded
            projectCatalog = screen.value.chat?.isUngrouped == false
            refresh(listOfNotNull(screen.value.live) + screen.value.messages)
        }
        screen.update {
            it.copy(filesVisible = visible, fileGroups = groups(it.fileSearch), removedAttachmentLinks = removedLinks())
        }
    }

    fun groups(query: String): List<FileGroup> = groups.filterFiles(query)

    fun refresh(messages: List<Message>) {
        val displayed = files(messages)
        val catalog = stored
        files = (displayed + catalog.orEmpty()).distinctBy(File::openId)
        val listed = if (catalog == null) displayed else {
            // Use persisted membership for saved attachments and retain unpublished live files.
            catalog + displayed.filter {
                it.storedAttachmentId == null && it.id !in removedFileIds && it.catalogKey !in removedFileKeys
            }
        }
        val next = if (projectCatalog) listed.distinctBy(File::openId) else listed.groupBy(File::catalogKey).values.map { versions ->
            val newest = versions.first()
            val firstDate = versions.asSequence().mapNotNull(File::date).minWithOrNull(FileDateOrder)
            if (firstDate == newest.date) newest else newest.copy(date = firstDate)
        }.distinctBy(File::id)
        if (next != listedFiles) {
            listedFiles = next
            groups = prepareFileGroups(next)
        }
    }

    suspend fun removeUnavailable(
        screen: MutableStateFlow<AiStudioScreenState>,
        storage: StudioWorkspaceStorage,
        sessionId: String,
        snapshotId: String,
    ) {
        val selected = screen.value
        if (selected.chat?.id != sessionId) return
        val file = stored?.find { it.openId == snapshotId } ?: return
        try {
            storage.removeUnavailableAttachment(sessionId, snapshotId)
        } catch (_: StudioRepositoryException) {
            screen.update { it.copy(storageError = "Не удалось удалить запись о вложении") }
            return
        }
        if (projectCatalog) {
            refreshCatalog(screen, storage)
            return
        }
        removedFileKeys += file.catalogKey
        stored = stored?.filterNot {
            val removed = it.catalogKey == file.catalogKey
            if (removed) removedFileIds += it.id
            removed
        }
        refresh(listOfNotNull(selected.live) + selected.messages)
        screen.update { it.copy(fileGroups = groups(it.fileSearch), removedAttachmentLinks = removedLinks()) }
    }

    private fun removedLinks(): Set<String> {
        val catalog = stored ?: return emptySet()
        val listed = catalog.mapTo(mutableSetOf(), File::openId)
        return files.filter {
            it.deferredReading && if (it.storedAttachmentId != null) it.openId !in listed
            else it.id in removedFileIds || it.catalogKey in removedFileKeys
        }.mapTo(mutableSetOf(), File::openId)
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
                is Reply -> {
                    message.result?.let(::add)
                    addAll(message.artifacts)
                    for (step in message.steps) for (event in step.events) addAll(event.files)
                }
            }
        }
    }.distinctBy(File::openId)
}

private val File.catalogKey: String
    get() = catalogUri ?: managedCopyPath?.let { "managed:$it" } ?: originalUri ?: "file-id:$id"

private val FileDateOrder = compareBy(FileDate::year, FileDate::month, FileDate::day, FileDate::time)
