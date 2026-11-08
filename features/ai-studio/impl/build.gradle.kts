import app.cash.sqldelight.gradle.SqlDelightExtension

plugins {
    id("heartbeat-kmp-universal")
    id("heartbeat-metro")
    id("heartbeat-sqldelight")
}

configure<SqlDelightExtension> {
    databases {
        create("HeartbeatDatabase") {
            packageName.set("io.github.aequicor.heartbeat.features.aistudio.db")
        }
    }
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":features:ai-studio:api"))
        implementation(project(":di:api"))
        implementation(project(":core:preferences:api"))
        implementation(libs.kotlinx.coroutines.core)
    }
    sourceSets.commonTest.dependencies {
        implementation(libs.kotlinx.coroutines.test)
    }
}
