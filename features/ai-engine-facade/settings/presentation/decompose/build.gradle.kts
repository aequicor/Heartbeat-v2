plugins {
    id("heartbeat-kmp-decompose")
    id("heartbeat-metro")
}

kotlin {
    sourceSets.jvmMain.dependencies {
        api(project(":features:ai-engine-facade:settings:impl"))
        implementation(project(":di:api"))
    }
}
