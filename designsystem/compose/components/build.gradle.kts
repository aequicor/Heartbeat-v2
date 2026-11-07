plugins {
    id("heartbeat-compose-designsystem")
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":designsystem:foundation"))
        api(project(":designsystem:compose:theme"))
        api(project(":designsystem:compose:resources"))
        implementation(compose.components.resources)
    }
    sourceSets.jvmTest.dependencies {
        implementation(libs.compose.ui)
    }
}

compose.resources {
    generateResClass = never
}