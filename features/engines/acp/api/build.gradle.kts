import heartbeat.KmpDefaults

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("java-library")
}

kotlin {
    explicitApi()
    jvmToolchain {
        languageVersion = KmpDefaults.JAVA_LANGUAGE_VERSION
        vendor = KmpDefaults.JVM_VENDOR
    }
}

dependencies {
    api(project(":features:ai-engine:api"))
}
