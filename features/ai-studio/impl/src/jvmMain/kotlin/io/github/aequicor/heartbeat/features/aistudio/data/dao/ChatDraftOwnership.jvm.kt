package io.github.aequicor.heartbeat.features.aistudio.data.dao

import app.cash.sqldelight.db.QueryResult
import io.github.aequicor.heartbeat.core.database.DatabaseConnection
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

internal actual fun chatDraftOwnership(connection: DatabaseConnection): ChatDraftOwnership =
    DesktopChatDraftOwnership(connection)

private class DesktopChatDraftOwnership(connection: DatabaseConnection) : ChatDraftOwnership {
    private val directory by lazy {
        connection.databasePath()?.let { path -> path.resolveSibling("${path.fileName}.chat-draft-owners") }
    }

    override fun claim(): Uuid? {
        val directory = directory ?: return null
        return try {
            owners.computeIfAbsent(directory, ::lockOwner).id
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    override fun ended(owner: Uuid): Boolean {
        val directory = directory ?: return false
        // On some systems closing any channel for this file releases our process's lock.
        if (owners[directory]?.id == owner) return false
        return try {
            FileChannel.open(directory.resolve(owner.toString()), WRITE).use { channel ->
                channel.tryLock()?.use { true } ?: false
            }
        } catch (_: OverlappingFileLockException) {
            false
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}

private data class LockedOwner(val id: Uuid, val lock: FileLock)

// Retain the lock for the life of the JVM, including when another graph opens the same database.
private val owners = ConcurrentHashMap<Path, LockedOwner>()

private fun lockOwner(directory: Path): LockedOwner {
    Files.createDirectories(directory)
    val owner = Uuid.random()
    val channel = FileChannel.open(directory.resolve(owner.toString()), WRITE, CREATE_NEW)
    try {
        return LockedOwner(owner, channel.lock())
    } catch (failure: IOException) {
        channel.close()
        throw failure
    }
}

private fun DatabaseConnection.databasePath(): Path? {
    val file = driver.executeQuery(
        identifier = null,
        sql = "PRAGMA database_list",
        mapper = { cursor ->
            var file: String? = null
            while (cursor.next().value) {
                if (cursor.getString(1) == "main") {
                    file = cursor.getString(2)
                    break
                }
            }
            QueryResult.Value(file)
        },
        parameters = 0,
    ).value?.takeIf(String::isNotEmpty) ?: return null
    return try {
        Path.of(file).toRealPath()
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
}
