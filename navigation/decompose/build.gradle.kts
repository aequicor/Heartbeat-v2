plugins {
    id("heartbeat-kmp-decompose")
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":navigation:api"))
        api(libs.kotlinx.coroutines.core)
    }
}
