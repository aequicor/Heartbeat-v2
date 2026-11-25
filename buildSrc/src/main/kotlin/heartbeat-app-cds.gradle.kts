import heartbeat.JcefJdkInfo
import org.gradle.api.tasks.JavaExec
import org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask
import java.nio.file.Files
import java.nio.file.StandardCopyOption

val jcefJdk = extensions.getByType(JcefJdkInfo::class.java)
val isWindows = System.getProperty("os.name").lowercase().startsWith("windows")

// Compose assigns JVM arguments and task defaults in its own afterEvaluate callback.
afterEvaluate {
    val devAppCdsArchive = layout.buildDirectory.file("appcds/Heartbeat.jsa")
    tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach {
        jvmArgs(
            "-XX:+AutoCreateSharedArchive",
            "-XX:SharedArchiveFile=${devAppCdsArchive.get().asFile.absolutePath}",
        )
        doFirst {
            Files.createDirectories(devAppCdsArchive.get().asFile.parentFile.toPath())
        }
    }

    tasks.withType<AbstractJLinkTask>().configureEach {
        doLast {
            val runtimeHome = destinationDir.get().asFile
            val javaExecutable = if (isWindows) "java.exe" else "java"
            val javaLauncher = runtimeHome.resolve("bin/$javaExecutable")
            Files.copy(
                jcefJdk.packagingJavaHome.resolve("bin/$javaExecutable").toPath(),
                javaLauncher.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
            try {
                if (!isWindows) {
                    check(javaLauncher.setExecutable(true, false)) { "Cannot make the Java launcher executable" }
                }
                providers.exec {
                    commandLine(javaLauncher.absolutePath, "-Xshare:dump")
                }.result.get()
            } finally {
                Files.deleteIfExists(javaLauncher.toPath())
            }
            val baseCdsArchivePath = if (isWindows) "bin/server/classes.jsa" else "lib/server/classes.jsa"
            val baseCdsArchive = runtimeHome.resolve(baseCdsArchivePath)
            check(baseCdsArchive.isFile) { "Desktop runtime is missing base CDS archive: $baseCdsArchive" }
        }
    }
}
