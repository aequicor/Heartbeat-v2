package io.github.aequicor.heartbeat.ai.engine.acp

import kotlin.test.Test
import kotlin.test.assertEquals

class AcpProcessTest {

    private fun spec(
        executable: String,
        arguments: List<String> = listOf("--profile", "acp"),
    ): AcpLaunchSpec = AcpLaunchSpec(executable = executable, arguments = arguments)

    @Test
    fun windowsRunsBatchShimsThroughTheInterpreter() {
        assertEquals(
            listOf("cmd.exe", "/c", "dsh-acp-server", "--profile", "acp"),
            launchCommand(spec("dsh-acp-server"), isWindows = true),
        )
        assertEquals(
            listOf("cmd.exe", "/c", "dsh.cmd", "--profile", "acp"),
            launchCommand(spec("dsh.cmd"), isWindows = true),
        )
        assertEquals(
            listOf("cmd.exe", "/c", "dsh", "--profile", "acp"),
            launchCommand(spec("dsh"), isWindows = true),
        )
    }

    @Test
    fun windowsRunsNativeExecutablesDirectly() {
        assertEquals(
            listOf("dsh.exe", "--profile", "acp"),
            launchCommand(spec("dsh.exe"), isWindows = true),
        )
        assertEquals(
            listOf("C:\\tools\\dsh.exe", "--profile", "acp"),
            launchCommand(spec("C:\\tools\\dsh.exe"), isWindows = true),
        )
    }

    @Test
    fun posixRunsTheExecutableDirectly() {
        assertEquals(
            listOf("dsh", "--profile", "acp"),
            launchCommand(spec("dsh"), isWindows = false),
        )
        assertEquals(
            listOf("/usr/local/bin/dsh-acp-server", "--profile", "acp"),
            launchCommand(spec("/usr/local/bin/dsh-acp-server"), isWindows = false),
        )
    }

    @Test
    fun environmentAndWorkingDirectoryStayInTheSpec() {
        val spec = AcpLaunchSpec(
            executable = "dsh",
            arguments = listOf("--profile", "acp"),
            environment = mapOf("DEEPSEEK_API_KEY" to "secret"),
            workingDirectory = "/workspace",
        )

        assertEquals("secret", spec.environment["DEEPSEEK_API_KEY"])
        assertEquals("/workspace", spec.workingDirectory)
    }
}
