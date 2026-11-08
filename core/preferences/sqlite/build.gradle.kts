import app.cash.sqldelight.gradle.SqlDelightExtension

plugins {
    id("heartbeat-kmp-universal")
    id("heartbeat-sqldelight")
}

configure<SqlDelightExtension> {
    databases {
        create("HeartbeatDatabase") {
            packageName.set("io.github.aequicor.heartbeat.core.preferences.sqlite.db")
        }
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:preferences:api"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.sqldelight.coroutines.extensions)
        }
        getByName("jvmTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}
