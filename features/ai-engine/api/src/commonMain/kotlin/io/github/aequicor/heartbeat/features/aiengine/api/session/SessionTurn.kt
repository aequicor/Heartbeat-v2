package io.github.aequicor.heartbeat.features.aiengine.api.session

public data class SessionTurn(
    val id: String,
    val status: Status,
    val accepted: Boolean = false,
    val failure: Failure? = null,
) {

    public enum class Status {
        Running, Completed, Stopped,

        /** The engine confirmed the prompt was not admitted. */
        Rejected,

        /** Delivery could not be confirmed. */
        SendFailed,

        /** The engine accepted the prompt and failed while producing the reply. */
        Failed,
    }

    public enum class Failure { RateLimited, ImageUnsupported, EmbeddedResourceUnsupported }
}
