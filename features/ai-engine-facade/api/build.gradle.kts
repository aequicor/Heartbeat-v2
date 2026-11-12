plugins {
    id("heartbeat-kmp-api")
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":features:ai-engine:api"))
        api(libs.kotlinx.coroutines.core)
    }
}
