package io.github.aequicor.heartbeat.di

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.aequicor.heartbeat.ai.engine.api.AiEngineFacade
import io.github.aequicor.heartbeat.core.database.JvmSqlDriverFactory
import io.github.aequicor.heartbeat.core.database.SqlDriverFactory
import io.github.aequicor.heartbeat.core.preferences.api.PreferencesStoreFactory
import io.github.aequicor.heartbeat.core.preferences.datastore.DataStorePreferencesStoreFactory
import io.github.aequicor.heartbeat.navigation.compose.ComposeFeatureRenderer
import io.github.aequicor.heartbeat.shell.ShellComponentFactory
import io.github.aequicor.heartbeat.storage.database.DatabaseInitializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Composition root of the desktop application.
 *
 * The graph lives in the platform compilation (HB-ARCH-014), so Metro aggregates every feature
 * contribution that is on the desktop classpath: the `Set<DecomposeFeatureEntry>` is consumed by
 * [ShellComponentFactory], while the platform pulls the Compose renderers directly for the shell
 * UI. Infrastructure bindings (database, preferences, application coroutine scope) are provided
 * here because only the entry point knows the platform storage directory.
 */
@DependencyGraph(AppScope::class)
interface DesktopGraph {

    val shellComponentFactory: ShellComponentFactory

    val renderers: Set<ComposeFeatureRenderer>

    val databaseInitializer: DatabaseInitializer

    /** AI engines of the desktop app; every contributed provider joins this catalog. */
    val aiEngineFacade: AiEngineFacade

    val appCoroutineScope: AppCoroutineScope

    @DependencyGraph.Factory
    fun interface Factory {

        fun create(@Provides dataDirectory: String): DesktopGraph
    }

    @Provides
    @SingleIn(AppScope::class)
    @Suppress("InjectDispatcher") // The composition root owns the application dispatcher.
    fun provideAppCoroutineScope(): AppCoroutineScope =
        AppCoroutineScope(CoroutineScope(SupervisorJob() + Dispatchers.IO))

    @Provides
    fun providePreferencesStoreFactory(
        dataDirectory: String,
        appCoroutineScope: AppCoroutineScope,
    ): PreferencesStoreFactory =
        DataStorePreferencesStoreFactory(dataDirectory, appCoroutineScope)

    @Provides
    fun provideSqlDriverFactory(dataDirectory: String): SqlDriverFactory =
        JvmSqlDriverFactory(dataDirectory)
}
