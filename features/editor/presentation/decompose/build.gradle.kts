plugins {
    id("heartbeat-kmp-decompose")
    id("heartbeat-metro")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":features:editor:api"))
            implementation(project(":di:api"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
