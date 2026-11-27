package io.github.aequicor.heartbeat.features.aistudio.presentation.chat

import io.github.aequicor.heartbeat.features.aistudio.isSubagentToolName
import io.github.aequicor.heartbeat.features.aistudio.subagentNameFromOutput

internal fun AiStudioScreenState.WorkEvent.isSubagentLaunch(): Boolean = title?.isSubagentToolName() == true

internal fun AiStudioScreenState.WorkEvent.subagentTitle(): String =
    detailLabel ?: details.subagentNameFromOutput() ?: "Субагент"
