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

    // Test-only: the composition root graph must see the feature bindings the platform
    // wires together. The app itself does not depend on features yet.
    testImplementation(project(":features:editor:api"))
    testImplementation(project(":features:editor:impl"))
    testImplementation(project(":features:editor:presentation:decompose"))
    testImplementation(project(":di:api"))
    testImplementation(libs.metro.runtime)
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
