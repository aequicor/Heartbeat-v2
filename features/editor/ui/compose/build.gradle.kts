plugins {
    id("heartbeat-kmp-compose")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":features:editor:api"))
        implementation(project(":designsystem:compose:components"))
    }
}
