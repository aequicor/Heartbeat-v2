import heartbeat.KmpDefaults
import org.gradle.api.tasks.PathSensitivity

// JVM-only module that runs Konsist architecture tests over the sources of every other module.
// It reads sources from disk (it does not depend on them), so a change in any module's sources
// must rerun the tests: the whole `src` tree is registered as a test input below.
plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain {
        languageVersion = KmpDefaults.JAVA_LANGUAGE_VERSION
    }
}

dependencies {
    testImplementation(libs.konsist)
    testImplementation(libs.junit)
}

val architectureSources = rootProject.fileTree(rootProject.projectDir) {
    include("**/src/**/*.kt")
    exclude(
        "**/build/**",
        "**/.gradle/**",
        "architecture-tests/**",
    )
}

tasks.test {
    useJUnit()
    workingDir(rootProject.projectDir)

    inputs.files(architectureSources)
        .withPropertyName("architectureSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    testLogging {
        events("failed", "skipped")
    }
}
