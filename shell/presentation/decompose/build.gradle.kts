plugins {
    id("heartbeat-kmp-decompose")
    id("heartbeat-metro")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":shell:api"))
        implementation(project(":navigation:api"))
        implementation(project(":navigation:decompose"))
        implementation(project(":di:api"))
    }
    sourceSets.commonTest.dependencies {
        implementation(libs.kotlinx.coroutines.test)
    }
}
