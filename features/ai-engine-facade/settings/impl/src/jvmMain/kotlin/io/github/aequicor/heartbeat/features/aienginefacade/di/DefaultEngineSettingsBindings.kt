package io.github.aequicor.heartbeat.features.aienginefacade.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineProvider
import io.github.aequicor.heartbeat.features.aienginefacade.data.dao.EngineSettingsDao
import io.github.aequicor.heartbeat.features.aienginefacade.data.dao.EngineSettingsDaoImpl
import io.github.aequicor.heartbeat.features.aienginefacade.data.datasource.AcpConnectionCheckDataSourceImpl
import io.github.aequicor.heartbeat.features.aienginefacade.data.datasource.EngineSettingsDataSourceImpl
import io.github.aequicor.heartbeat.features.aienginefacade.data.datasource.SavedAcpEngineProviderDataSource
import io.github.aequicor.heartbeat.features.aienginefacade.domain.datasource.AcpConnectionCheckDataSource
import io.github.aequicor.heartbeat.features.aienginefacade.domain.datasource.EngineSettingsDataSource
import io.github.aequicor.heartbeat.features.aienginefacade.domain.interactor.EngineSettingsInteractor
import io.github.aequicor.heartbeat.features.aienginefacade.domain.interactor.EngineSettingsInteractorImpl
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsStoreFactory
import io.github.aequicor.heartbeat.features.aienginefacade.presentation.engines.EngineSettingsStoreFactoryImpl
import kotlinx.coroutines.flow.Flow

@BindingContainer
@ContributesTo(AppScope::class)
public interface DefaultEngineSettingsBindings {

    public companion object {

        @Provides
        internal fun dao(impl: EngineSettingsDaoImpl): EngineSettingsDao = impl

        @Provides
        internal fun storage(impl: EngineSettingsDataSourceImpl): EngineSettingsDataSource = impl

        @Provides
        internal fun probe(impl: AcpConnectionCheckDataSourceImpl): AcpConnectionCheckDataSource = impl

        @Provides
        internal fun interactor(impl: EngineSettingsInteractorImpl): EngineSettingsInteractor = impl

        @Provides
        internal fun storeFactory(impl: EngineSettingsStoreFactoryImpl): EngineSettingsStoreFactory = impl

        @Provides
        @IntoSet
        internal fun providers(saved: SavedAcpEngineProviderDataSource): Flow<List<AiEngineProvider>> = saved.observe()
    }
}
