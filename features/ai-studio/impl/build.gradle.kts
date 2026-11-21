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
        implementation(libs.kotlinx.serialization.json)
        api(libs.kotlinx.coroutines.core)
        implementation(project(":di:api"))
        implementation(project(":core:preferences:api"))
        api(project(":core:database"))
        implementation(libs.sqldelight.coroutines.extensions)
        implementation(libs.okio)
        api(project(":features:ai-engine-facade:api"))
    }
    sourceSets.commonTest.dependencies {
        implementation(libs.kotlinx.coroutines.test)
    }
    sourceSets.getByName("jvmTest").dependencies {
        implementation(libs.sqldelight.sqlite.driver)
    }
}
