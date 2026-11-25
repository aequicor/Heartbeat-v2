package heartbeat

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

/** Use the linked runtime's default archive so the installer ships a single, relocatable cache. */
fun prepareDesktopAotCache(
    launcher: File,
    runtimeHome: File,
    sdkHome: File,
    launcherJvmArgs: List<String>,
    temporaryDir: File,
) {
    val windows = System.getProperty("os.name").startsWith("Windows")
    val cache = runtimeHome.resolve(if (windows) "bin/server/classes.jsa" else "lib/server/classes.jsa")
    val work = Files.createTempDirectory(temporaryDir.toPath(), "aot-").toFile()
    val stock = work.resolve("stock.jsa")
    Files.move(cache.toPath(), stock.toPath())
    try {
        // The training run also verifies that the production launcher works with its cache missing.
        runDesktopProbe(launcher, work, "record", "-Xlog:class+load=info:file=classes.log:none")
        // JBR 25 resolves archived application CodeSource URLs against the build machine's JAR paths.
        // Only classes from the linked runtime image have stable origins (jrt:/) after installation.
        val classes = work.resolve("classes.log").useLines { lines ->
            lines.filter { " source: jrt:/" in it }
                .map { it.substringBefore(" source:").replace('.', '/') }
                .distinct().toList()
        }
        check("java/awt/Window" in classes) { "Training did not observe AWT window classes in the runtime image" }
        work.resolve("runtime.lst").writeText(classes.joinToString("\n", postfix = "\n"))
        dumpRuntimeCache(runtimeHome, sdkHome, launcherJvmArgs, work, cache)
        check(cache.length() > 0) { "The packaged JVM did not create $cache" }
        runDesktopProbe(launcher, work, "verify", "-XX:AOTMode=on -Xlog:aot=info:file=verify.log:none")
        check(work.resolve("verify.log").useLines { lines -> lines.any { "Using AOT-linked classes: true" in it } }) {
            "The packaged JVM did not load AOT-linked classes from $cache"
        }
    } catch (failure: Exception) {
        Files.move(stock.toPath(), cache.toPath(), StandardCopyOption.REPLACE_EXISTING)
        throw failure
    } finally {
        work.deleteRecursively()
    }
}

private fun dumpRuntimeCache(runtimeHome: File, sdkHome: File, launcherJvmArgs: List<String>, work: File, cache: File) {
    // jlink strips native commands. Use the matching Java launcher with the actual linked modules.
    val binary = if (System.getProperty("os.name").startsWith("Windows")) "bin/java.exe" else "bin/java"
    val java = runtimeHome.resolve(binary)
    val needsLauncher = !java.isFile
    if (needsLauncher) {
        Files.createDirectories(java.parentFile.toPath())
        Files.copy(sdkHome.resolve(binary).toPath(), java.toPath(), StandardCopyOption.COPY_ATTRIBUTES)
    }
    try {
        val emptyClassPath = Files.createDirectory(work.resolve("empty").toPath())
        val dumpArgs = listOf(
            "-Xshare:dump", "-XX:+AOTClassLinking", "-XX:SharedClassListFile=runtime.lst",
            "-XX:SharedArchiveFile=${cache.absolutePath}", "-cp", emptyClassPath.toString(),
        )
        val command = listOf(java.absolutePath) + launcherJvmArgs + dumpArgs
        runProcess(ProcessBuilder(command).directory(work), "create")
    } finally {
        if (needsLauncher) Files.delete(java.toPath())
    }
}

private fun runDesktopProbe(launcher: File, work: File, phase: String, options: String) {
    val marker = work.resolve("$phase.drawn")
    val needsDisplay = System.getProperty("os.name") == "Linux" && System.getenv("DISPLAY").isNullOrBlank()
    val command = if (needsDisplay) {
        listOf("xvfb-run", "-a", launcher.absolutePath)
    } else {
        listOf(launcher.absolutePath)
    }
    val builder = ProcessBuilder(command).directory(work)
    builder.environment().apply {
        put("JAVA_TOOL_OPTIONS", options)
        put("HEARTBEAT_DATA_DIR", work.resolve("$phase-data").absolutePath)
        put("HEARTBEAT_STARTUP_PROBE", marker.absolutePath)
        put("TMP", work.absolutePath)
        put("TEMP", work.absolutePath)
    }
    runProcess(builder, phase)
    check(marker.isFile) { "AOT $phase did not draw the Heartbeat shell" }
}

private fun runProcess(builder: ProcessBuilder, phase: String) {
    val process = builder.inheritIO().start()
    try {
        check(process.waitFor(3, TimeUnit.MINUTES)) { "Timed out during AOT $phase" }
        check(process.exitValue() == 0) { "AOT $phase exited with ${process.exitValue()}" }
    } finally {
        process.descendants().forEach { it.destroyForcibly() }
        process.destroyForcibly()
    }
}
