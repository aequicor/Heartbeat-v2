plugins {
    id("heartbeat-compose-designsystem")
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":designsystem:foundation"))
        api(project(":designsystem:compose:theme"))
    }
    sourceSets.jvmTest.dependencies {
        implementation(libs.compose.ui)
    }
}
