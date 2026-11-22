package io.github.aequicor.heartbeat.features.aienginefacade.data.datasource

import dev.zacsweers.metro.Inject
import io.github.aequicor.heartbeat.features.acp.api.AcpProviderFactory
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineConnection
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineKey
import io.github.aequicor.heartbeat.features.aiengine.api.AiEngineProvider
import io.github.aequicor.heartbeat.features.aienginefacade.data.dao.EngineSettingsDao
import io.github.aequicor.heartbeat.features.aienginefacade.data.entity.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@Inject
internal class SavedAcpEngineProviderDataSource(
    private val dao: EngineSettingsDao,
    private val factory: AcpProviderFactory,
) {

    fun observe(): Flow<List<AiEngineProvider>> = flow {
        var currentKeys = mapOf<String, MutableStateFlow<Key>>()
        dao.observeCatalog().map { records ->
            records.map { Key(it.id, it.name, it.connection_name) }
        }.distinctUntilChanged().collect { keys ->
            currentKeys = keys.associate { key ->
                val current = currentKeys[key.id] ?: MutableStateFlow(key)
                current.value = key
                key.id to current
            }
            emit(keys.map { key ->
                val current = currentKeys.getValue(key.id)
                factory.create(key, currentKey = { current.value }) {
                    val connection = dao.connectionFor(key.id).toDomain()
                    val arguments = requireNotNull(connection.launchArguments()) {
                        "Некорректные параметры запуска ACP"
                    }
                    AcpProviderFactory.Launch(listOf(connection.command) + arguments, connection.environment)
                }
            })
        }
    }

    private data class Key(
        override val id: String,
        override val name: String,
        override val description: String,
    ) : AiEngineKey {

        override val connection = AiEngineConnection.ACP
    }
}
