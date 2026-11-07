plugins {
    id("heartbeat-kmp-compose")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":features:settings:api"))
        implementation(project(":designsystem:compose:components"))
    }
}
