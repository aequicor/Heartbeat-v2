plugins {
    id("heartbeat-kmp-universal")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":ai:engine:api"))
    }
}
