plugins {
    id("heartbeat-kmp-universal")
}

// DataStore publishes Android, JVM, Apple and Linux targets but no mingwX64 artifact. The
// implementation therefore lives in a dedicated source set that only the supported targets
// depend on; Windows Native uses the SQLite `PreferencesStore` from `core/preferences/sqlite`.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:preferences:api"))
            implementation(libs.kotlinx.coroutines.core)
        }

        val dataStoreMain = maybeCreate("dataStoreMain")
        dataStoreMain.dependsOn(commonMain.get())
        dataStoreMain.dependencies {
            implementation(libs.datastore.preferences.core)
            implementation(libs.okio)
        }

        listOf("androidMain", "jvmMain", "iosMain", "macosMain", "linuxMain").forEach { name ->
            getByName(name).dependsOn(dataStoreMain)
        }

        getByName("jvmTest").dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
