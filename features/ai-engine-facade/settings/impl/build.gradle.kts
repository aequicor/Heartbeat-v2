import app.cash.sqldelight.gradle.SqlDelightExtension

plugins {
    id("heartbeat-kmp-universal")
    id("heartbeat-metro")
    alias(libs.plugins.kotlin.serialization)
    id("heartbeat-sqldelight")
}

configure<SqlDelightExtension> {
    databases {
        create("HeartbeatDatabase") {
            packageName.set("io.github.aequicor.heartbeat.features.aienginefacade.db")
        }
    }
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.sqldelight.coroutines.extensions)
        implementation(project(":core:database"))
        implementation(project(":di:api"))
    }
    sourceSets.jvmMain.dependencies {
        implementation(project(":features:engines:acp:api"))
    }
}
