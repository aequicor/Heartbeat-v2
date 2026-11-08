plugins {
    id("heartbeat-kmp-universal")
    id("heartbeat-metro")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":features:ai-studio:api"))
        implementation(project(":di:api"))
        implementation(libs.kotlinx.coroutines.core)
    }
}
