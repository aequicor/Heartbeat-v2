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
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

@OptIn(UnstableApi::class)
@ContributesBinding(AppScope::class)
public class AcpProbeApiImpl(@Named("io") private val ioDispatcher: CoroutineDispatcher) : AcpProbeApi {

    @Throwable(AcpProbeException::class, CancellationException::class)
    @Suppress("TooGenericExceptionCaught") // Keep the failing stage and omit process arguments and environment values.
    override suspend fun initialize(command: String, arguments: List<String>, environment: Map<String, String>): Int =
        withContext(ioDispatcher) {
            supervisorScope {
                val process = try {
                    startAcpProcess(listOf(command) + arguments, environment, ProcessBuilder.Redirect.DISCARD)
                } catch (failure: Exception) {
                    throw AcpProbeException(false, "Не удалось запустить программу ACP", failure)
                }
                var connection: AcpConnection? = null
                try {
                    val active = AcpConnection(process, this, ioDispatcher)
                    connection = active
                    withinRequestTimeout { active.initialize() }
                    active.agent.protocolVersion
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    throw AcpProbeException(true, "Агент не завершил ACP initialize", failure)
                } finally {
                    if (connection == null) process.terminateTree() else connection.close()
                }
            }
        }
}
