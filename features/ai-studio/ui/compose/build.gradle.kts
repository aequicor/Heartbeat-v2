plugins {
    id("heartbeat-kmp-compose")
    id("heartbeat-metro")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":designsystem:compose:components"))
        implementation(project(":features:ai-studio:api"))
        implementation(project(":features:ai-studio:presentation:decompose"))
        implementation(project(":features:settings:api"))
        implementation(project(":navigation:decompose-compose"))
        implementation(project(":di:api"))
    }
    sourceSets.jvmTest.dependencies {
        implementation(libs.compose.ui)
        implementation(compose.desktop.currentOs)
    }
}
