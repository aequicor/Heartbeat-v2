package io.github.aequicor.heartbeat.features.aistudio.domain.model

import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

@JvmInline
internal value class ProjectId(val value: Uuid) {
    companion object {
        val Ungrouped = ProjectId(Uuid.NIL)

        fun parse(value: String): ProjectId = ProjectId(Uuid.parse(value))
    }
}
