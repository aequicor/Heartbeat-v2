plugins {
    id("heartbeat-kmp-universal")
    id("heartbeat-metro")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":shell:api"))
        implementation(project(":di:api"))
    }
}
