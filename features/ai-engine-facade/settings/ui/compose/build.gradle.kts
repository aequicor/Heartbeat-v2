plugins {
    id("heartbeat-kmp-compose")
    id("heartbeat-metro")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(compose.components.resources)
        implementation(project(":designsystem:compose:components"))
        implementation(project(":features:ai-engine-facade:settings:impl"))
    }
    sourceSets.jvmMain.dependencies {
        implementation(libs.filekit.dialogs.compose)
        implementation(project(":features:ai-engine-facade:settings:presentation:decompose"))
        implementation(project(":features:settings:api"))
        implementation(project(":navigation:decompose-compose"))
        implementation(project(":di:api"))
    }
}

compose.resources {
    packageOfResClass = "io.github.aequicor.heartbeat.features.aienginefacade.ui.resources"
}
