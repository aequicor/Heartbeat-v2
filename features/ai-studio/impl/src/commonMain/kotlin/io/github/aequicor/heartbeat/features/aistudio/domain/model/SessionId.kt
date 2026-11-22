package io.github.aequicor.heartbeat.features.aistudio.domain.model

import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

@JvmInline
internal value class SessionId(val value: Uuid) {
    companion object {
        fun parse(value: String): SessionId = SessionId(Uuid.parse(value))
    }
}
