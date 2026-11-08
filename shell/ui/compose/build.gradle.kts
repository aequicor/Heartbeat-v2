plugins {
    id("heartbeat-kmp-compose")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":shell:api"))
        implementation(project(":shell:presentation:decompose"))
        implementation(project(":navigation:api"))
        implementation(project(":navigation:decompose"))
        implementation(project(":navigation:decompose-compose"))
        implementation(project(":designsystem:compose:components"))
        implementation(libs.decompose.extensions.compose)
    }
}
