import app.cash.sqldelight.gradle.SqlDelightExtension
import org.gradle.api.artifacts.VersionCatalogsExtension

// SQLDelight schema modules. Apply after a heartbeat-kmp-* plugin, which provides the Kotlin
// multiplatform target matrix, then declare the database in the module build file:
//
//   configure<SqlDelightExtension> {
//       databases { create("HeartbeatDatabase") { packageName.set("...") } }
//   }
//
// Every schema module uses the same database name. The aggregate in `storage/database` resolves
// `dependency(project(...))` by that name, so a renamed database silently breaks schema
// composition. The plugin adds the SQLDelight runtime to commonMain; target drivers stay with the
// module that creates physical connections (`core/database`), never with a feature.

project.pluginManager.apply("app.cash.sqldelight")

// Schema modules only generate query APIs; they do not link SQLite themselves. The native driver
// lives in core/database, which enables `linkSqlite` and owns the system SQLite dependency.
configure<SqlDelightExtension> {
    linkSqlite.set(false)
}

val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

dependencies {
    add("commonMainImplementation", libs.findLibrary("sqldelight-runtime").get())
}
