package io.github.aequicor.heartbeat.features.aistudio.domain.datasource

import io.github.aequicor.heartbeat.features.aistudio.domain.model.ProjectDirectory
import io.github.aequicor.heartbeat.features.aistudio.domain.model.PromptReference
import kotlin.Throws as Throwable
import kotlin.coroutines.cancellation.CancellationException

internal interface PromptReferenceDataSource {

    @Throwable(Exception::class, CancellationException::class)
    suspend fun search(directory: ProjectDirectory?, query: String): List<PromptReference>
}
