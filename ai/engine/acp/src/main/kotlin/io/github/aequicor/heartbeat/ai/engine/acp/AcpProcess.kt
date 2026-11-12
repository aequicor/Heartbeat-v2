package io.github.aequicor.heartbeat.ai.engine.acp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Standard input and output of a running agent.
 *
 * [input] streams stdout lines to the ACP transport; [write] sends one line to stdin. The process
 * is terminated with its descendants, because an ACP agent may spawn tool processes. The interface
 * exists so tests can run the adapter against an in-memory agent.
 */
internal interface AcpStdio {
    val input: Flow<String>

    suspend fun write(line: String)

    /** Last lines of stderr, used as the failure reason. */
    fun diagnostics(): String

    fun terminate()
}

/** [AcpStdio] backed by a child process. */
internal class ProcessStdio(
    private val process: Process,
    private val stderrBuffer: StringBuilder,
) : AcpStdio {

    override val input: Flow<String> = flow {
        val reader = process.inputStream.bufferedReader()
        try {
            while (true) {
                val line = reader.readLine() ?: break
                emit(line)
            }
        } finally {
            runCatching { reader.close() }
        }
    }

    @Suppress("SwallowedException", "RedundantSuspendModifier")
    // The signature matches the suspending ACP write callback; the stream write itself blocks.
    override suspend fun write(line: String) {
        try {
            val stream = process.outputStream
            stream.write(line.toByteArray())
            stream.write('\n'.code)
            stream.flush()
        } catch (error: IOException) {
            // The agent closed stdin; the transport reports the closed connection itself.
        }
    }

    override fun diagnostics(): String = synchronized(stderrBuffer) { stderrBuffer.toString().trim() }

    override fun terminate() {
        runCatching { process.descendants().forEach { it.destroy() } }
        process.destroy()
        if (!process.waitFor(TERMINATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            runCatching { process.descendants().forEach { it.destroyForcibly() } }
            process.destroyForcibly()
        }
    }

    private companion object {
        private const val TERMINATE_TIMEOUT_MILLIS = 2_000L
    }
}

/** Starts the agent process; the seam lets tests replace the process with an in-memory agent. */
internal fun interface AcpProcessStarter {
    fun start(spec: AcpLaunchSpec, scope: CoroutineScope): AcpStdio
}

internal object AcpProcessLauncher : AcpProcessStarter {

    @Suppress("InjectDispatcher") // The launcher owns the stderr drain; no dispatcher is injected here.
    override fun start(spec: AcpLaunchSpec, scope: CoroutineScope): AcpStdio {
        val builder = ProcessBuilder(command(spec))
        val workingDirectory = spec.workingDirectory
        if (workingDirectory != null) {
            builder.directory(java.io.File(workingDirectory))
        }
        builder.environment().putAll(spec.environment)
        builder.redirectErrorStream(false)
        val process = builder.start()
        val stderr = StringBuilder()
        scope.launch(Dispatchers.IO) {
            runCatching {
                process.errorStream.bufferedReader().forEachLine { line ->
                    synchronized(stderr) {
                        stderr.append(line).append('\n')
                        if (stderr.length > MAX_STDERR_CHARS) {
                            stderr.delete(0, stderr.length - MAX_STDERR_CHARS)
                        }
                    }
                }
            }
        }
        return ProcessStdio(process, stderr)
    }

    /**
     * Windows cannot start a `.cmd` or `.bat` shim directly, and the `dsh` and `dsh-acp-server`
     * commands are such shims (or extensionless wrappers). Everything that is not an `.exe` goes
     * through the command interpreter; a POSIX host runs the executable as is.
     */
    private fun command(spec: AcpLaunchSpec): List<String> =
        launchCommand(spec, isWindows = System.getProperty("os.name").startsWith("windows", true))

    private const val MAX_STDERR_CHARS = 4_000
}

/** Builds the process command line; pure so the Windows shim rule is unit-tested. */
internal fun launchCommand(spec: AcpLaunchSpec, isWindows: Boolean): List<String> {
    val name = spec.executable.replace('\\', '/').substringAfterLast('/')
    val needsInterpreter = isWindows && !name.endsWith(".exe", ignoreCase = true)
    val executable = if (needsInterpreter) {
        listOf("cmd.exe", "/c", spec.executable)
    } else {
        listOf(spec.executable)
    }
    return executable + spec.arguments
}
