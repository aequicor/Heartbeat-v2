import heartbeat.KmpDefaults

// Kotlin Multiplatform library with the full mandatory target set from
// docs/specs/architecture.md: Android, JVM, all Apple targets, Windows mingwX64,
// Linux x64 and arm64. Use for universal modules: feature api/impl, shell api/impl,
// core and di. Apple targets compile only on macOS hosts; on other hosts the Kotlin
// Gradle plugin disables them.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        namespace = KmpDefaults.namespace(project.path)
        compileSdk = KmpDefaults.COMPILE_SDK
        minSdk = KmpDefaults.MIN_SDK
    }

    jvm()
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    macosArm64()
    macosX64()
    mingwX64()
    linuxX64()
    linuxArm64()

    jvmToolchain {
        languageVersion = KmpDefaults.JAVA_LANGUAGE_VERSION
        vendor = KmpDefaults.JVM_VENDOR
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
