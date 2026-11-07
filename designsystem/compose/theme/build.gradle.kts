plugins {
    id("heartbeat-compose-designsystem")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(libs.compose.ui)
        implementation(project(":designsystem:foundation"))
    }
}
