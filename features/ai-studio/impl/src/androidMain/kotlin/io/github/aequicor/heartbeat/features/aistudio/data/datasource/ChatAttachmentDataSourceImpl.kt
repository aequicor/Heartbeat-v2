package io.github.aequicor.heartbeat.features.aistudio.data.datasource

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatAttachmentDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@Inject
@ContributesBinding(AppScope::class)
internal class ChatAttachmentDataSourceImpl(
    private val context: Context,
    @Named("io") private val ioDispatcher: CoroutineDispatcher,
) : ChatAttachmentDataSource {

    @Throwable(Exception::class, CancellationException::class)
    override suspend fun read(reference: String, maxBytes: Int): Pair<String, ByteArray> = withContext(ioDispatcher) {
        val uri = Uri.parse(reference)
        require(uri.scheme == ContentResolver.SCHEME_CONTENT)
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        require(!name.isNullOrBlank())
        val stream = requireNotNull(resolver.openInputStream(uri))
        name to stream.source().buffer().use { it.readAttachmentBytes(maxBytes) }
    }
}
