package io.github.aequicor.heartbeat.features.acp.data

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.Throws as Throwable

@Throwable(IOException::class, IllegalArgumentException::class, SecurityException::class)
internal fun startAcpProcess(
    command: List<String>,
    environment: Map<String, String>,
    stderr: ProcessBuilder.Redirect,
): Process {
    val builder = ProcessBuilder(command).redirectError(stderr)
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        require(environment.keys.toSortedSet(String.CASE_INSENSITIVE_ORDER).size == environment.size) {
            "В Windows имена переменных среды должны быть уникальны без учёта регистра"
        }
        val inherited = builder.environment()
        for ((key, value) in environment) {
            val existing = inherited.keys.firstOrNull { it.equals(key, ignoreCase = true) }
            inherited[existing ?: key] = value
        }
        val executable = windowsExecutable(command.first(), builder.environment())
        val extension = executable.substringAfterLast('.', "").lowercase()
        if (extension == "cmd" || extension == "bat") {
            builder.command(batchCommand(executable, command.drop(1)))
        } else {
            builder.command(nativeWindowsCommand(executable, command.drop(1)))
        }
    } else {
        builder.environment().putAll(environment)
    }
    return builder.start()
}

private fun nativeWindowsCommand(executable: String, arguments: List<String>): List<String> {
    val allowAmbiguous = System.getProperty("jdk.lang.Process.allowAmbiguousCommands", "true")
    if (allowAmbiguous.equals("false", ignoreCase = true)) {
        // The strict JDK encoder treats already quoted values as command-line syntax.
        require(arguments.none { it.length >= 2 && it.startsWith('"') && it.endsWith('"') }) {
            "JVM в строгом режиме запуска Windows не поддерживает буквальные кавычки на обоих концах аргумента."
        }
        return listOf(executable) + arguments
    }
    // The legacy JDK encoder forwards prequoted values verbatim. Encode literal quotes for native argv here.
    return listOf(executable) + arguments.map { if ('"' in it) quoteWindowsArgument(it) else it }
}

private fun quoteWindowsArgument(argument: String): String = buildString {
    append('"')
    var backslashes = 0
    for (character in argument) {
        if (character == '\\') {
            backslashes++
        } else {
            val count = if (character == '"') 2 * backslashes + 1 else backslashes
            repeat(count) { append('\\') }
            append(character)
            backslashes = 0
        }
    }
    repeat(2 * backslashes) { append('\\') }
    append('"')
}

private fun batchCommand(executable: String, arguments: List<String>): List<String> {
    // CALL reparses its input. Exclude expansion and quoting characters before inserting any user values.
    require((listOf(executable) + arguments).none { value -> value.any { it in BatchMetacharacters } }) {
        "Пакетный скрипт не поддерживает кавычки и управляющие символы cmd в пути или аргументах. Укажите файл .exe."
    }
    val shell = Path.of(System.getenv("SystemRoot"), "System32", "cmd.exe").toString()
    val quotedExecutable = "\"$executable\""
    val scriptArguments = arguments.map { argument ->
        if (argument.isEmpty() || argument.any { it.isWhitespace() || it in BatchDelimiters }) {
            require(!argument.endsWith('\\')) {
                "Пакетный скрипт не поддерживает аргумент, который требует кавычек и заканчивается на \\. " +
                    "Укажите файл .exe."
            }
            "\"$argument\""
        } else {
            argument
        }
    }
    return listOf(shell, "/d", "/v:off", "/c", "call", quotedExecutable) + scriptArguments
}

private fun windowsExecutable(program: String, environment: Map<String, String>): String {
    val path = Path.of(program)
    val directories = if (path.isAbsolute || path.parent != null) {
        listOf(Path.of(""))
    } else {
        val searchPath = environment.entries.firstOrNull { it.key.equals("PATH", ignoreCase = true) }?.value.orEmpty()
        searchPath.split(';').filter(String::isNotBlank).map { Path.of(it.trim('"')) }
    }
    val suffixes = if ('.' in path.fileName.toString()) listOf("") else listOf(".exe", ".com", ".cmd", ".bat")
    for (directory in directories) {
        for (suffix in suffixes) {
            val candidate = directory.resolve(program + suffix)
            if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath().toString()
        }
    }
    return program
}

private const val BatchMetacharacters = "\"%&|<>^!\r\n"
private const val BatchDelimiters = "(),;="
