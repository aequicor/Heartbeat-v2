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
            packageName.set("io.github.aequicor.heartbeat.features.aistudio.db")
        }
    }
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(libs.jetbrains.markdown)
        implementation(libs.okio)
        implementation(libs.kotlinx.serialization.json)
        api(libs.kotlinx.coroutines.core)
        implementation(project(":di:api"))
        implementation(project(":core:preferences:api"))
        implementation(project(":core:database"))
    }
    sourceSets.commonTest.dependencies {
        implementation(libs.kotlinx.coroutines.test)
    }
    sourceSets.jvmTest.dependencies {
        implementation(libs.sqldelight.sqlite.driver)
    }
}
