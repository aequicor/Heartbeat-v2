plugins {
    id("heartbeat-kmp-compose")
    id("heartbeat-metro")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(compose.components.resources)
        implementation(project(":features:settings:api"))
        implementation(project(":features:settings:presentation:decompose"))
        implementation(project(":navigation:decompose-compose"))
        implementation(project(":designsystem:compose:components"))
        implementation(project(":di:api"))
        implementation(libs.kotlinx.coroutines.core)
    }
}

compose.resources {
    packageOfResClass = "io.github.aequicor.heartbeat.features.settings.ui.resources"
}
