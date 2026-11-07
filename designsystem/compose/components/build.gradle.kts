plugins {
    id("heartbeat-compose-designsystem")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":designsystem:compose:theme"))
    }
}
