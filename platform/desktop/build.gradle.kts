import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import java.io.File
import org.gradle.api.GradleException

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    // The composition root lives in the platform compilation (HB-ARCH-014), so the Metro
    // compiler plugin runs here and the integration test graph proves that the feature
    // contributions of every module assemble into one graph.
    id("dev.zacsweers.metro")
}

val desktopVersion = "1.0.0"
val isWindows = System.getProperty("os.name").lowercase().startsWith("windows")

kotlin {
    // Jewel 0.42.0 is built for the IntelliJ Platform 2026.3 runtime and ships
    // class files for Java 25, so the application must run on JBR 25.
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.JETBRAINS
    }
}

dependencies {
    implementation(compose.desktop.currentOs) {
        // Jewel provides the Int UI controls; keep Material off the desktop classpath.
        exclude(group = "org.jetbrains.compose.material")
    }
    implementation(libs.jewel.int.ui.standalone)
    implementation(libs.jewel.decorated.window)
    implementation(project(":designsystem:compose:theme"))
    implementation(project(":navigation:api"))
    implementation(project(":navigation:decompose"))
    implementation(project(":navigation:decompose-compose"))
    implementation(project(":shell:presentation:decompose"))
    implementation(project(":shell:ui:compose"))
    implementation(project(":features:ai-studio:api"))
    implementation(project(":features:ai-studio:impl"))
    implementation(project(":features:ai-studio:presentation:decompose"))
    implementation(project(":features:ai-studio:ui:compose"))
    implementation(project(":features:settings:api"))
    implementation(project(":features:settings:impl"))
    implementation(project(":features:settings:presentation:decompose"))
    implementation(project(":features:settings:ui:compose"))
    implementation(project(":di:api"))
    implementation(project(":core:database"))
    implementation(project(":core:preferences:api"))
    implementation(project(":core:preferences:datastore"))
    implementation(project(":storage:database"))
    implementation(libs.metro.runtime)

    // Test-only: the composition root graph must see the feature bindings the platform
    // wires together. The application itself composes the AI studio screen only.
    testImplementation(project(":features:editor:api"))
    testImplementation(project(":features:editor:impl"))
    testImplementation(project(":features:editor:presentation:decompose"))
    testImplementation(project(":features:ai-studio:api"))
    testImplementation(project(":features:ai-studio:impl"))
    testImplementation(project(":features:ai-studio:presentation:decompose"))
    testImplementation(project(":features:ai-studio:ui:compose"))
    testImplementation(project(":features:settings:api"))
    testImplementation(project(":features:settings:impl"))
    testImplementation(project(":features:settings:presentation:decompose"))
    testImplementation(project(":features:settings:ui:compose"))
    testImplementation(project(":shell:presentation:decompose"))
    testImplementation(project(":navigation:decompose"))
    testImplementation(project(":navigation:decompose-compose"))
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "io.github.aequicor.heartbeat.MainKt"

        // DecoratedWindow needs the JetBrains Runtime, including the runtime image
        // that jpackage builds for the distributable.
        javaHome = javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(25)
            vendor = JvmVendorSpec.JETBRAINS
        }.get().metadata.installationPath.asFile.absolutePath

        nativeDistributions {
            // SQLite JDBC loads java.sql; jpackage does not include it automatically.
            modules("java.sql")
            // Windows is packaged by Inno Setup (packageInnoSetup), not by the
            // built-in Compose formats (Msi/Exe), so no Windows format is listed.
            targetFormats(TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "Heartbeat"
            packageVersion = desktopVersion
        }
    }
}

// Resolve the Inno Setup compiler (ISCC.exe) from explicit environment paths,
// standard install directories, or finally PATH.
val isccCandidates = listOfNotNull(
    providers.environmentVariable("ISCC").orNull,
    providers.environmentVariable("INNO_SETUP_HOME").orNull?.let { "$it/ISCC.exe" },
    providers.environmentVariable("LOCALAPPDATA").orNull
        ?.let { "$it/Programs/Inno Setup 6/ISCC.exe" },
    providers.environmentVariable("ProgramFiles(x86)").orNull
        ?.let { "$it/Inno Setup 6/ISCC.exe" },
    providers.environmentVariable("ProgramFiles").orNull
        ?.let { "$it/Inno Setup 6/ISCC.exe" },
).filter { File(it).isFile }

val appImageDir = layout.buildDirectory.dir("compose/binaries/main/app/Heartbeat")
val installerDir = layout.buildDirectory.dir("compose/binaries/main/innosetup")

// On Windows, a sandboxed dev environment can make the system temp directory read-only.
// The app then crashes at startup: Jewel's IntUiTheme measures the Inter font with
// Font.createFont, and the JDK writes a temporary font file into java.io.tmpdir. Redirect
// the launched app's temp directory to a writable directory under the build output. This
// affects only the local run task, not the packaged distribution.
if (isWindows) {
    val runTmpDir = layout.buildDirectory.dir("run-tmp")

    val launchApp = tasks.register<Exec>("runDistributableWritableTemp") {
        group = "compose desktop"
        description = "Runs the packaged desktop app with a writable temp directory"
        dependsOn("createDistributable")
        workingDir = appImageDir.get().asFile
        commandLine(appImageDir.get().asFile.resolve("Heartbeat.exe").absolutePath)
        environment("TMP", runTmpDir.get().asFile.absolutePath)
        environment("TEMP", runTmpDir.get().asFile.absolutePath)
        doFirst {
            runTmpDir.get().asFile.mkdirs()
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
    onlyIf { isWindows }
    workingDir = projectDir
    executable = isccCandidates.firstOrNull() ?: "ISCC.exe"
    args(
        "/DAppVersion=$desktopVersion",
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
        if (isccCandidates.isEmpty() &&
            System.getenv("PATH").orEmpty().split(File.pathSeparator)
                .filter { it.isNotEmpty() }
                .none { File(it, "ISCC.exe").isFile }
        ) {
            throw GradleException(
                "Inno Setup 6.3 or newer is required to build the Windows installer. " +
                    "Set ISCC or INNO_SETUP_HOME, or add ISCC.exe to PATH.",
            )
        }
    }
}

tasks.matching { it.name == "packageDistributionForCurrentOS" }.configureEach {
    dependsOn(packageInnoSetup)
}
