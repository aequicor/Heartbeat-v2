plugins {
    id("heartbeat-kmp-api")
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(libs.metro.runtime)
    }
}
