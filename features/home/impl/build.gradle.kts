plugins {
    id("heartbeat-kmp-universal")
    id("heartbeat-metro")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":features:home:api"))
        implementation(project(":di:api"))
    }
}
