plugins {
    id("heartbeat-kmp-decompose")
    id("heartbeat-metro")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":shell:api"))
        implementation(project(":di:api"))
    }
}
