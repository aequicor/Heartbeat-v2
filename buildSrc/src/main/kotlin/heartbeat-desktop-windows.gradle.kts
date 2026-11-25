import heartbeat.InnoSetupInfo
import heartbeat.JcefJdkInfo
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

plugins {
    id("org.jetbrains.compose")
    id("heartbeat-jcef-jdk")
    id("heartbeat-inno-setup")
}

val jcefJdk = extensions.getByType(JcefJdkInfo::class.java)
val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

if (isWindows) {
    val jnaJar = configurations.getByName("runtimeClasspath").incoming.artifactView {
        componentFilter { component ->
            component is ModuleComponentIdentifier &&
                component.group == "net.java.dev.jna" && component.module == "jna"
        }
    }.files
    val jnaResources = layout.buildDirectory.dir("jna-launcher-resources")
    val jnaLibrary = jnaResources.map { it.file("windows/jna/jnidispatch.dll") }
    val jnaArchitecture = when (jcefJdk.hostArchitecture) {
        "x64" -> "x86-64"
        "arm64" -> "aarch64"
        else -> "x86"
    }
    val prepareWindowsJna = tasks.register("prepareWindowsJna") {
        inputs.files(jnaJar)
        inputs.property("jnaArchitecture", jnaArchitecture)
        outputs.file(jnaLibrary)
        doLast {
            // Use the native library from the same JNA version as the runtime classpath.
            ZipFile(jnaJar.singleFile).use { jar ->
                val entryPath = "com/sun/jna/win32-$jnaArchitecture/jnidispatch.dll"
                val entry = checkNotNull(jar.getEntry(entryPath)) { "JNA is missing $entryPath" }
                val target = jnaLibrary.get().asFile.toPath()
                Files.createDirectories(target.parent)
                jar.getInputStream(entry).use { source ->
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }
    compose.desktop.application.nativeDistributions {
        appResourcesRootDir.set(prepareWindowsJna.map { jnaResources.get() })
    }
}

val innoSetup = extensions.getByType(InnoSetupInfo::class.java)
val appImageDir = layout.buildDirectory.dir("compose/binaries/main/app/Heartbeat")
val distribution = compose.desktop.application.nativeDistributions
val installerDir = layout.buildDirectory.dir("compose/binaries/main/innosetup")

// On Windows, a sandboxed dev environment can make the system temp directory and the user profile
// read-only. The app then crashes at startup: Jewel's IntUiTheme measures the Inter font with
// Font.createFont, the JDK writes a temporary font file into java.io.tmpdir, and SQLite cannot
// create the database under %LOCALAPPDATA%. Redirect both the temp and the application data
// directory to writable directories under the build output. This affects only the local run task,
// not the packaged distribution.
if (isWindows) {
    val runTmpDir = layout.buildDirectory.dir("run-tmp")
    val runDataDir = layout.buildDirectory.dir("run-data")

    // runDev uses the installed JBR so ACP agents inherit the developer's process permissions.
    tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach {
        environment("HEARTBEAT_DATA_DIR", runDataDir.get().asFile.absolutePath)
    }

    val launchApp = tasks.register<Exec>("runDistributableWritableTemp") {
        group = "compose desktop"
        description = "Runs the packaged desktop app with writable temp and data directories"
        dependsOn("createDistributable")
        workingDir = appImageDir.get().asFile
        commandLine(appImageDir.get().asFile.resolve("Heartbeat.exe").absolutePath)
        environment("TMP", runTmpDir.get().asFile.absolutePath)
        environment("TEMP", runTmpDir.get().asFile.absolutePath)
        environment("HEARTBEAT_DATA_DIR", runDataDir.get().asFile.absolutePath)
        doFirst {
            runTmpDir.get().asFile.mkdirs()
            runDataDir.get().asFile.mkdirs()
        }
    }

    tasks.matching { it.name == "runDistributable" }.configureEach {
        actions.clear()
        dependsOn(launchApp)
    }
}

val packageInnoSetup = tasks.register<Exec>("packageInnoSetup") {
    group = "compose desktop"
    description = "Build the Windows installer with Inno Setup"
    dependsOn("createDistributable")
    innoSetup.provisionTask?.let { dependsOn(it) }
    onlyIf { isWindows }
    workingDir = projectDir
    executable = innoSetup.compiler.absolutePath
    args(
        "/DAppVersion=${checkNotNull(distribution.packageVersion)}",
        "/DSourceDir=${appImageDir.get().asFile.absolutePath}",
        "/DOutputDir=${installerDir.get().asFile.absolutePath}",
        "installer/heartbeat.iss",
    )
    doFirst {
        val image = appImageDir.get().asFile
        check(File(image, "Heartbeat.exe").isFile) {
            "Application image is missing: $image. Run createDistributable first."
        }
        installerDir.get().asFile.mkdirs()
    }
}

tasks.matching { it.name == "packageDistributionForCurrentOS" }.configureEach {
    dependsOn(packageInnoSetup)
}
