@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)

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
        implementation(compose.uiTest)
        implementation(compose.desktop.currentOs)
    }
}

compose.resources {
    generateResClass = never
}
