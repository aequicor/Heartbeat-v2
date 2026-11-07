@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)

plugins {
    id("heartbeat-compose-designsystem")
}

kotlin {
    sourceSets.jvmMain.dependencies {
        implementation(project(":designsystem:foundation"))
        implementation(project(":designsystem:compose:theme"))
        implementation(project(":designsystem:compose:components"))
        implementation(compose.desktop.currentOs)
    }
    sourceSets.jvmTest.dependencies {
        implementation(compose.uiTest)
        implementation(project(":designsystem:foundation"))
        implementation(project(":designsystem:compose:theme"))
        implementation(project(":designsystem:compose:components"))
        implementation(compose.desktop.currentOs)
    }
}

// The sandbox is a desktop application for review, not a product entry point: it lives in the
// design system boundary and depends on the published kit only.
val jvmRuntimeClasspath = configurations.getByName("jvmRuntimeClasspath")

tasks.register<JavaExec>("runSandbox") {
    group = "application"
    description = "Runs the design system sandbox in a desktop window."
    dependsOn(tasks.named("jvmJar"))
    classpath(tasks.named("jvmJar"), jvmRuntimeClasspath)
    mainClass.set("io.github.aequicor.heartbeat.designsystem.sandbox.HbSandboxEntry")
}
