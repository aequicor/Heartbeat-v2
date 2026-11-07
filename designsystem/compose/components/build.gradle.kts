plugins {
    id("heartbeat-compose-designsystem")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":designsystem:foundation"))
        implementation(project(":designsystem:compose:theme"))
    }
    sourceSets.jvmTest.dependencies {
        // The catalog renders the appearance enum and the theme directly, and an offscreen
        // scene needs the skiko native runtime of the current host.
        implementation(project(":designsystem:foundation"))
        implementation(project(":designsystem:compose:theme"))
        implementation(compose.desktop.currentOs)
    }
}
