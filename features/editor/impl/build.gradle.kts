import app.cash.sqldelight.gradle.SqlDelightExtension

plugins {
    id("heartbeat-kmp-universal")
    id("heartbeat-metro")
    id("heartbeat-sqldelight")
}

configure<SqlDelightExtension> {
    databases {
        create("HeartbeatDatabase") {
            packageName.set("io.github.aequicor.heartbeat.features.editor.db")
        }
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":features:editor:api"))
            implementation(project(":di:api"))
            implementation(project(":core:database"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("jvmTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}
