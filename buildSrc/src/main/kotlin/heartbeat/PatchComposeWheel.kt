package heartbeat

import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.ExecOperations
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/** Patches the timeout/receive race in the pinned Compose Desktop Foundation JAR. */
@CacheableTransform
abstract class PatchComposeWheel : TransformAction<PatchComposeWheel.Parameters> {
    interface Parameters : TransformParameters {
        @get:Classpath
        val sources: ConfigurableFileCollection

        @get:Classpath
        val foundationClasspath: ConfigurableFileCollection

        @get:Classpath
        val compilerClasspath: ConfigurableFileCollection
    }

    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val artifact: Provider<FileSystemLocation>

    @get:Inject
    abstract val exec: ExecOperations

    override fun transform(outputs: TransformOutputs) {
        val input = artifact.get().asFile
        if (!input.name.startsWith("foundation-desktop-")) {
            outputs.file(input)
            return
        }
        check(input.name == PinnedJar) {
            "The Desktop wheel fix is for $PinnedJar. Review the upstream fix for ${input.name}."
        }
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(input.inputStream(), digest).use { it.transferTo(OutputStream.nullOutputStream()) }
        check(digest.digest().toHexString() == PinnedJarSha256) { "Unexpected bytes for $PinnedJar" }
        val output = outputs.file(input.name)
        val work = Files.createTempDirectory(output.parentFile.toPath(), "wheel-").toFile()
        try {
            val source = work.resolve("MouseWheelScrollingLogic.kt")
            source.writeText(patchedSource())
            val compiled = work.resolve("classes")
            compile(source, input, compiled)
            replaceWheelClasses(input, compiled, output)
        } finally {
            work.deleteRecursively()
        }
    }

    private fun patchedSource(): String {
        val entry = "commonMain/$WheelPath.kt"
        val source = ZipFile(parameters.sources.singleFile).use { jar ->
            val file = checkNotNull(jar.getEntry(entry)) { "Compose Foundation source $entry is missing" }
            jar.getInputStream(file).use { it.readBytes() }
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(source).toHexString()
        check(hash == PinnedSourceSha256) { "Unexpected Compose Foundation wheel source: $hash" }
        return source.decodeToString()
            .replace("import kotlinx.coroutines.withTimeoutOrNull", AtomicWaitImports)
            .replace("withTimeoutOrNull(timeoutMillis) { channel.busyReceive() }", "channel.awaitWheelDelta(timeoutMillis)")
            .plus(AtomicWaitSource)
    }

    private fun compile(source: File, foundation: File, output: File) {
        exec.javaexec {
            classpath = parameters.compilerClasspath
            mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
            args(
                "-no-stdlib", "-no-reflect", "-jvm-target", "11",
                "-classpath", parameters.foundationClasspath.asPath,
                "-Xfriend-paths=${foundation.absolutePath}",
                "-module-name", "foundation",
                "-d", output.absolutePath, source.absolutePath,
            )
        }.assertNormalExitValue()
    }

    private fun replaceWheelClasses(input: File, compiled: File, output: File) {
        val replacements = compiled.walkTopDown().filter(File::isFile)
            .associateBy { it.relativeTo(compiled).invariantSeparatorsPath }.filterKeys(::isWheelClass)
        check("$WheelPath.class" in replacements && "${WheelPath}Kt.class" in replacements) {
            "Compose wheel patch did not compile both expected class files"
        }
        ZipFile(input).use { original ->
            val entries = original.entries().asSequence().toList()
            check(entries.any { it.name == "$WheelPath.class" }) { "The original Compose wheel classes are missing" }
            check(entries.none { it.name.matches(JarSignature) }) { "Cannot patch a signed Compose Foundation JAR" }
            ZipOutputStream(output.outputStream().buffered()).use { jar ->
                for (entry in entries) {
                    if (isWheelClass(entry.name)) continue
                    jar.putNextEntry(stableEntry(entry.name))
                    original.getInputStream(entry).use { it.copyTo(jar) }
                    jar.closeEntry()
                }
                for ((path, replacement) in replacements.toSortedMap()) {
                    jar.putNextEntry(stableEntry(path))
                    replacement.inputStream().use { it.copyTo(jar) }
                    jar.closeEntry()
                }
            }
        }
    }
}

private const val PinnedJar = "foundation-desktop-1.12.2.jar"
private const val PinnedJarSha256 = "e1dc29cd9de2f1628a741866a112eedc62fec43bc548d0e4f434bf31207fd1e3"
private const val PinnedSourceSha256 = "d4013155477b81025bd02ed95a41d583ced0c8b55d9be43fd9b04557c0941882"
private const val WheelPath = "androidx/compose/foundation/gestures/MouseWheelScrollingLogic"
private val JarSignature = Regex("META-INF/[^/]+\\.(SF|RSA|DSA|EC)", RegexOption.IGNORE_CASE)

private fun isWheelClass(path: String): Boolean =
    path == "$WheelPath.class" || path == "${WheelPath}Kt.class" ||
        ((path.startsWith("${WheelPath}\$") || path.startsWith("${WheelPath}Kt\$")) && path.endsWith(".class"))

private fun stableEntry(name: String): ZipEntry = ZipEntry(name).apply {
    setTimeLocal(LocalDateTime.of(1980, 1, 1, 0, 0))
}

private val AtomicWaitImports = """
    import androidx.compose.runtime.withFrameNanos
    import kotlinx.coroutines.ExperimentalCoroutinesApi
    import kotlinx.coroutines.coroutineScope
    import kotlinx.coroutines.selects.onTimeout
    import kotlinx.coroutines.selects.select
""".trimIndent()

private val AtomicWaitSource = """

    // Use one selection for timeout and receive so a timeout cannot discard an already received delta.
    @OptIn(ExperimentalCoroutinesApi::class)
    internal suspend fun <T : Any> Channel<T>.awaitWheelDelta(timeoutMillis: Long): T? {
        if (timeoutMillis <= 0) return null
        return coroutineScope {
            // As in busyReceive, keep requesting frames while the gesture is active.
            val frames = launch {
                while (coroutineContext.isActive) withFrameNanos {}
            }
            try {
                select<T?> {
                    onReceive { it }
                    onTimeout(timeoutMillis) { null }
                }
            } finally {
                frames.cancel()
            }
        }
    }
""".trimIndent().let { "\n\n$it\n" }
