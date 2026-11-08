plugins {
    id("heartbeat-kmp-compose")
    id("heartbeat-metro")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":features:settings:api"))
        implementation(project(":features:settings:presentation:decompose"))
        implementation(project(":navigation:decompose-compose"))
        implementation(project(":designsystem:compose:components"))
        implementation(project(":di:api"))
        implementation(libs.kotlinx.coroutines.core)
    }
}
