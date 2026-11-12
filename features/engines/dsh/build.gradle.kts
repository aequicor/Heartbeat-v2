import heartbeat.KmpDefaults

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("dev.zacsweers.metro")
}

kotlin {
    jvmToolchain {
        languageVersion = KmpDefaults.JAVA_LANGUAGE_VERSION
        vendor = KmpDefaults.JVM_VENDOR
    }
}

dependencies {
    implementation(project(":di:api"))
    implementation(libs.metro.runtime)
}
