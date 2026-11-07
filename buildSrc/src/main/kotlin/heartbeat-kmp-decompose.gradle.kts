import heartbeat.KmpDefaults
import org.gradle.api.artifacts.VersionCatalogsExtension

// Decompose presentation adapter with the targets Decompose 3.6 publishes.
// Windows Native is excluded: Decompose has no mingwX64 artifacts. The Decompose
// dependency is an `api` dependency because component contracts expose Decompose
// types such as Value and ChildStack.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
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
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    macosArm64()
    macosX64()
    linuxX64()
    linuxArm64()

    jvmToolchain {
        languageVersion = KmpDefaults.JAVA_LANGUAGE_VERSION
        vendor = KmpDefaults.JVM_VENDOR
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.findLibrary("decompose").get())
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
