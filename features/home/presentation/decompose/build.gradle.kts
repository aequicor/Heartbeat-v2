plugins {
    id("heartbeat-kmp-decompose")
    id("heartbeat-metro")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":features:home:api"))
        implementation(project(":di:api"))
    }
}
