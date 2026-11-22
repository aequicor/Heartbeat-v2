package io.github.aequicor.heartbeat.features.acp.data.api

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

private val StderrPollInterval = 25.milliseconds
private val AccessError = Regex("""\b(EPERM|EACCES)\b""")

/** Drain stderr independently of ACP stdout. Only a known error code and exit status leave the adapter. */
internal suspend fun Process.readProbeDiagnostic(): String? {
    val prefix = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    try {
        do {
            currentCoroutineContext().ensureActive()
            val running = isAlive
            val available = minOf(errorStream.available(), buffer.size)
            if (available > 0) {
                val read = errorStream.read(buffer, 0, available)
                if (read < 0) break
                prefix.write(buffer, 0, minOf(read, buffer.size - prefix.size()))
            } else if (running) {
                delay(StderrPollInterval)
            }
        } while (running || available > 0)
    } catch (_: IOException) {
        // Some Windows pipes report that the handle is closed instead of returning EOF.
    }
    val code = AccessError.find(prefix.toString(Charsets.UTF_8))?.value
    return listOfNotNull(
        code?.let { "Движок сообщил об отказе в доступе ($it)." },
        if (isAlive) null else "Движок завершился с кодом ${exitValue()}.",
    ).takeIf { it.isNotEmpty() }?.joinToString(" ")
}
