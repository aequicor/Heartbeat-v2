package io.github.aequicor.heartbeat.features.acp.data.api

import com.agentclientprotocol.annotations.UnstableApi
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Named
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.acp.api.AcpProbeApi
import io.github.aequicor.heartbeat.features.acp.api.AcpProbeException
import io.github.aequicor.heartbeat.features.acp.data.AcpConnection
import io.github.aequicor.heartbeat.features.acp.data.startAcpProcess
import io.github.aequicor.heartbeat.features.acp.data.terminateTree
import io.github.aequicor.heartbeat.features.acp.data.withinRequestTimeout
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

private val DiagnosticWait = 250.milliseconds

@OptIn(UnstableApi::class)
@ContributesBinding(AppScope::class)
public class AcpProbeApiImpl(@Named("io") private val ioDispatcher: CoroutineDispatcher) : AcpProbeApi {

    @Throwable(AcpProbeException::class, CancellationException::class)
    @Suppress("TooGenericExceptionCaught") // Keep the failing stage and omit process arguments and environment values.
    override suspend fun initialize(command: String, arguments: List<String>, environment: Map<String, String>): Int =
        withContext(ioDispatcher) {
            supervisorScope {
                val process = try {
                    startAcpProcess(listOf(command) + arguments, environment, ProcessBuilder.Redirect.PIPE)
                } catch (failure: Exception) {
                    throw AcpProbeException(false, "Не удалось запустить программу ACP", failure)
                }
                var connection: AcpConnection? = null
                val diagnostic = async { process.readProbeDiagnostic() }
                try {
                    val active = AcpConnection(process, this, ioDispatcher)
                    connection = active
                    withinRequestTimeout { active.initialize() }
                    active.agent.protocolVersion
                } catch (failure: Exception) {
                    // The SDK also cancels the pending request when the agent closes its output.
                    currentCoroutineContext().ensureActive()
                    val message = withTimeoutOrNull(DiagnosticWait) { diagnostic.await() }
                        ?: "Не получен совместимый ответ ACP initialize"
                    throw AcpProbeException(true, message, failure)
                } finally {
                    diagnostic.cancel()
                    if (connection == null) process.terminateTree() else connection.close()
                }
            }
        }
}
