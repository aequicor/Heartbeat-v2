package io.github.aequicor.heartbeat.features.aistudio.ui.components

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@Suppress("InjectDispatcher") // The picker owns the background provider query.
internal actual fun rememberProjectFolderLauncher(
    onSelect: (path: String, name: String) -> Unit,
    onCancel: () -> Unit,
    onError: () -> Unit,
): () -> Unit {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    val contract = remember { ActivityResultContracts.OpenDocumentTree() }
    val launcher = rememberLauncherForActivityResult(contract) { uri ->
        if (uri == null) onCancel()
        else scope.launch {
            try {
                val name = withContext(Dispatchers.IO) { resolver.persistProjectFolder(uri) }
                if (name != null) onSelect(uri.toString(), name) else onError()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                onError()
            }
        }
    }
    return {
        try {
            launcher.launch(null)
        } catch (_: Exception) {
            onError()
        }
    }
}

private fun ContentResolver.persistProjectFolder(tree: Uri): String? {
    val document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    val columns = arrayOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
    val name = query(document, columns, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && cursor.getString(1) == Document.MIME_TYPE_DIR) {
            cursor.getString(0)?.takeIf(String::isNotBlank)
        } else null
    }
    if (name != null) takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return name
}
