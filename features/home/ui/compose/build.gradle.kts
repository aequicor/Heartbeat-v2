plugins {
    id("heartbeat-kmp-compose")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":features:home:api"))
        implementation(project(":designsystem:compose:components"))
    }
}
