plugins {
    id("heartbeat-kmp-universal")
    id("heartbeat-metro")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":ai:engine:api"))
        implementation(project(":di:api"))
        implementation(libs.kotlinx.coroutines.core)
    }
    sourceSets.commonTest.dependencies {
        implementation(libs.kotlinx.coroutines.test)
    }
}
