import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

val desktopVersion = "1.0.0"
val isWindows = System.getProperty("os.name").lowercase().startsWith("windows")

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "io.github.aequicor.heartbeat.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "Heartbeat"
            packageVersion = desktopVersion
        }
    }
}

val iscc = providers.environmentVariable("ISCC").orNull
    ?: providers.environmentVariable("INNO_SETUP_HOME").orNull?.let { "$it/ISCC.exe" }
    ?: listOfNotNull(
        providers.environmentVariable("LOCALAPPDATA").orNull
            ?.let { "$it/Programs/Inno Setup 6/ISCC.exe" },
        providers.environmentVariable("ProgramFiles(x86)").orNull
            ?.let { "$it/Inno Setup 6/ISCC.exe" },
        providers.environmentVariable("ProgramFiles").orNull
            ?.let { "$it/Inno Setup 6/ISCC.exe" },
    ).firstOrNull { File(it).isFile }
    ?: "ISCC.exe"

val appImageDir = layout.buildDirectory.dir("compose/binaries/main/app/Heartbeat")
val installerDir = layout.buildDirectory.dir("compose/binaries/main/innosetup")

val packageInnoSetup = tasks.register<Exec>("packageInnoSetup") {
    group = "compose desktop"
    description = "Build the Windows installer with Inno Setup"
    dependsOn("createDistributable")
    onlyIf { isWindows }
    workingDir = projectDir
    executable = iscc
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
    }
}

tasks.matching { it.name == "packageDistributionForCurrentOS" }.configureEach {
    dependsOn(packageInnoSetup)
}
