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
        api(project(":features:ai-engine:api"))
        api(libs.kotlinx.coroutines.core)
        implementation(project(":features:ai-engine-facade:api"))
        implementation(project(":di:api"))
        implementation(project(":core:preferences:api"))
        implementation(libs.okio)
    }
    sourceSets.commonTest.dependencies {
        implementation(libs.kotlinx.coroutines.test)
    }
}
