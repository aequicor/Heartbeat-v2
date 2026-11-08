import app.cash.sqldelight.gradle.SqlDelightExtension

plugins {
    id("heartbeat-kmp-universal")
    // The SQLDelight Gradle plugin supplies the native SQLite linker option. Apply it without a
    // database: this module creates physical drivers, it owns no schema.
    id("app.cash.sqldelight")
}

configure<SqlDelightExtension> {
    linkSqlite.set(true)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.sqldelight.runtime)
        }
        getByName("jvmMain").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        getByName("androidMain").dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        getByName("nativeMain").dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }
}
