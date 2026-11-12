plugins {
    id("heartbeat-kmp-universal")
    id("heartbeat-metro")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":features:ai-engine-facade:api"))
        implementation(project(":di:api"))
        implementation(libs.kotlinx.atomicfu)
    }
    sourceSets.commonTest.dependencies {
        implementation(libs.kotlinx.coroutines.test)
    }
}
