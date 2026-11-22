package io.github.aequicor.heartbeat.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@BindingContainer
@ContributesTo(AppScope::class)
public interface DesktopDispatcherBindings {

    public companion object {

        @Provides
        @Named("io")
        @Suppress("InjectDispatcher")
        public fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @Named("engine-settings-default")
        @Suppress("InjectDispatcher")
        public fun provideEngineSettingsDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

        @Provides
        @Named("engine-settings-ui")
        @Suppress("InjectDispatcher")
        public fun provideEngineSettingsUiDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate
    }
}
