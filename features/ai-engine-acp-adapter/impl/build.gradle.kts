import heartbeat.KmpDefaults

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("java-library")
    id("dev.zacsweers.metro")
}

kotlin {
    jvmToolchain {
        languageVersion = KmpDefaults.JAVA_LANGUAGE_VERSION
        vendor = KmpDefaults.JVM_VENDOR
    }
}

dependencies {
    api(project(":features:ai-engine:api"))
    api(project(":di:api"))
    implementation(libs.acp)
    implementation(libs.metro.runtime)
    runtimeOnly(libs.slf4j.api)
    testImplementation(kotlin("test-junit"))
}

tasks.test {
    systemProperty("heartbeat.acp.test.classpath", sourceSets.test.get().runtimeClasspath.asPath)
}
