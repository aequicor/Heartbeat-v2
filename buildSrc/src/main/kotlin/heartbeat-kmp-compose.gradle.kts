import heartbeat.KmpDefaults

// Compose Multiplatform module with Android and JVM targets. Compose is not
// available for Linux/Windows Native; Apple targets stay optional and a module
// adds them explicitly when it needs them.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.kotlin.multiplatform.library")
}

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
            implementation(compose.runtime)
            implementation(compose.foundation)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
