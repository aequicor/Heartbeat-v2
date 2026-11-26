plugins {
    id("heartbeat-kmp-decompose")
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":navigation:api"))
        api(project(":designsystem:foundation"))
        api(libs.kotlinx.coroutines.core)
    }
}
