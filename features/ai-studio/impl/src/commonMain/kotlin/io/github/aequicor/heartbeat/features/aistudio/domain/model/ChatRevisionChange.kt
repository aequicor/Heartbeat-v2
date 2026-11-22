package io.github.aequicor.heartbeat.features.aistudio.domain.model

internal enum class ChatRevisionChange {

    Append, UpdateDraft, Replace, Ignore;

    companion object {

        fun decide(storedStatus: String?, previouslyVisible: Boolean, shared: Boolean): ChatRevisionChange = when {
            storedStatus == null -> if (previouslyVisible) Ignore else Append
            storedStatus == "draft" && !shared -> UpdateDraft
            storedStatus == "draft" || previouslyVisible -> Replace
            else -> Ignore
        }
    }
}
