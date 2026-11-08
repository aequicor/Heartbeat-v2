import app.cash.sqldelight.gradle.SqlDelightExtension

plugins {
    id("heartbeat-kmp-universal")
    id("heartbeat-metro")
    id("heartbeat-sqldelight")
}

// The aggregate owns the physical schema and its version. Feature modules provide `.sq` files and
// are never queried for their schema at runtime: `dependency` composes them at build time. Only
// add a module here when it actually contains SQLDelight sources.
configure<SqlDelightExtension> {
    databases {
        create("HeartbeatDatabase") {
            packageName.set("io.github.aequicor.heartbeat.storage.db")
            // 1.db is the checked-in baseline snapshot of the first schema version. Later
            // migrations live only in src/commonMain/sqldelight/migrations and bump the aggregate
            // version; dependent feature modules keep only their latest .sq and need no migration
            // configuration of their own.
            schemaOutputDirectory.set(
                project.layout.projectDirectory.dir("src/commonMain/sqldelight/databases"),
            )
            verifyMigrations.set(true)

            dependency(project(":features:editor:impl"))
            dependency(project(":features:ai-studio:impl"))
            dependency(project(":core:preferences:sqlite"))
        }
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:database"))
            implementation(project(":core:preferences:sqlite"))
            implementation(project(":di:api"))
            // The generated aggregate database extends the generated interfaces of every
            // schema it composes, so the schema modules are also compile dependencies.
            implementation(project(":features:editor:impl"))
            implementation(project(":features:ai-studio:impl"))
        }
        getByName("jvmTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}
