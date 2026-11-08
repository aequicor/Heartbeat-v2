plugins {
    id("heartbeat-kmp-universal")
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.serialization.core)
    }
}
