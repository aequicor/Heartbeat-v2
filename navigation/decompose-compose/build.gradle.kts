plugins {
    id("heartbeat-kmp-compose")
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":navigation:decompose"))
    }
}
