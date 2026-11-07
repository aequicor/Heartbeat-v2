import heartbeat.KmpDefaults
import org.gradle.api.artifacts.VersionCatalogsExtension

// Compose Multiplatform module with Android and JVM targets. Compose is not
// available for Linux/Windows Native; Apple targets stay optional and a module
// adds them explicitly when it needs them.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.kotlin.multiplatform.library")
}

val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

kotlin {
    android {
        namespace = KmpDefaults.namespace(project.path)
        compileSdk = KmpDefaults.COMPILE_SDK
        minSdk = KmpDefaults.MIN_SDK
    }

    jvm()

    jvmToolchain {
        languageVersion = KmpDefaults.JAVA_LANGUAGE_VERSION
        vendor = KmpDefaults.JVM_VENDOR
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.findLibrary("compose-runtime").get())
            implementation(libs.findLibrary("compose-foundation").get())
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
