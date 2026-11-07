plugins {
    id("heartbeat-kmp-universal")
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(libs.metro.runtime)
    }
}
