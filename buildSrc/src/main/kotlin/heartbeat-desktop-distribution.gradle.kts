import heartbeat.JcefJdkInfo
import heartbeat.prepareDesktopAotCache
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

plugins {
    id("org.jetbrains.compose")
    id("heartbeat-jcef-jdk")
    id("heartbeat-app-cds")
}

val jcefJdk = extensions.getByType(JcefJdkInfo::class.java)
val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
val isMacos = System.getProperty("os.name").startsWith("Mac")
val jcefModule = jcefJdk.packagingJavaHome.resolve("jmods/jcef.jmod")
val frameworks = jcefJdk.packagingJavaHome.parentFile.resolve("Frameworks")
val windowsLauncherManifest = layout.projectDirectory.file("installer/Heartbeat.exe.manifest")

tasks.withType<AbstractJPackageTask>().matching { it.targetFormat == TargetFormat.AppImage }.configureEach {
    if (!jcefJdk.hasJcef) {
        doFirst {
            error("Packaging Heartbeat requires JetBrains JDK 25 with JCEF. Set JAVA_HOME or JBR_JCEF_HOME to that SDK.")
        }
        return@configureEach
    }
    if (isWindows) inputs.files(jcefModule, windowsLauncherManifest)
    if (isMacos) inputs.dir(frameworks)
    doLast {
        val name = packageName.get()
        val image = destinationDir.get().asFile.resolve(if (isMacos) "$name.app" else name)
        val runtime = image.resolve(when {
            isMacos -> "Contents/runtime"
            isWindows -> "runtime"
            else -> "lib/runtime"
        })
        val launcher = image.resolve(when {
            isMacos -> "Contents/MacOS/$name"
            isWindows -> "$name.exe"
            else -> "bin/$name"
        })

        if (isWindows) {
            // JBR draws caption buttons in a layered child window, which requires a Windows-aware launcher.
            windowsLauncherManifest.asFile.copyTo(image.resolve("${launcher.name}.manifest"), overwrite = true)
        }
        if (isMacos) {
            // jpackage copies the linked Java image but omits its sibling Frameworks.
            providers.exec {
                commandLine("/usr/bin/ditto", frameworks, runtime.resolve("Contents/Frameworks"))
            }.result.get()
        } else {
            prepareJcefLibraries(runtime, jcefModule, isWindows)
        }

        val runtimeHome = if (isMacos) runtime.resolve("Contents/Home") else runtime
        prepareDesktopAotCache(launcher, runtimeHome, jcefJdk.packagingJavaHome, launcherJvmArgs.get(), temporaryDir)

        if (isMacos) {
            for (bundle in listOf(runtime, image)) {
                providers.exec { commandLine("/usr/bin/codesign", "--force", "--sign", "-", bundle) }.result.get()
            }
        }
    }
}

// Compose normally rebuilds Debian packages from the jars, which would discard the trained image.
if (!isWindows && !isMacos) {
    afterEvaluate {
        tasks.withType<AbstractJPackageTask>().matching { it.targetFormat == TargetFormat.Deb }.configureEach {
            val imageTaskName = name.replaceFirst("package", "create").removeSuffix("Deb") + "Distributable"
            val image = tasks.named<AbstractJPackageTask>(imageTaskName)
            dependsOn(image)
            appImage.set(image.flatMap { it.destinationDir.dir(it.packageName) })
        }
    }
}

private fun prepareJcefLibraries(runtime: File, jcefModule: File, isWindows: Boolean) {
    val nativeDirectory = runtime.resolve(if (isWindows) "bin" else "lib")
    if (isWindows) {
        // Compose's jlink task strips the bin section of JCEF along with JVM commands.
        val runtimeBin = nativeDirectory.toPath()
        ZipFile(jcefModule).use { module ->
            for (entry in module.entries()) {
                if (!entry.isDirectory && entry.name.startsWith("bin/")) {
                    val target = runtimeBin.resolve(entry.name.removePrefix("bin/")).normalize()
                    check(target.startsWith(runtimeBin)) { "Invalid path in jcef.jmod: ${entry.name}" }
                    Files.createDirectories(target.parent)
                    module.getInputStream(entry).use { source ->
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        }
    }
    val requiredFiles = if (isWindows) {
        listOf("jcef.dll", "libcef.dll", "jcef_helper.exe", "cef_server.exe", "icudtl.dat", "resources.pak")
    } else {
        listOf("libjcef.so", "libcef.so", "jcef_helper", "cef_server", "icudtl.dat", "resources.pak")
    }
    val missing = requiredFiles.filterNot { nativeDirectory.resolve(it).isFile }
    check(missing.isEmpty()) { "Desktop runtime is missing JCEF files: ${missing.joinToString()}" }
    if (!isWindows) {
        // jmod stores the executable bytes without Unix mode bits.
        for (helper in listOf("jcef_helper", "cef_server")) {
            check(nativeDirectory.resolve(helper).setExecutable(true, false)) {
                "Cannot make the JCEF helper executable: $helper"
            }
        }
    }
}
