plugins {
    id("heartbeat-kmp-compose")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":shell:api"))
        implementation(project(":designsystem:compose:components"))
    }
}
