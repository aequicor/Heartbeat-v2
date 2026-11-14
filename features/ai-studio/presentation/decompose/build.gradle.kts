plugins {
    id("heartbeat-kmp-decompose")
    id("heartbeat-metro")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":features:ai-studio:api"))
        implementation(project(":features:ai-studio:impl"))
        implementation(project(":features:settings:api"))
        implementation(project(":navigation:decompose"))
        implementation(project(":di:api"))
    }
}
