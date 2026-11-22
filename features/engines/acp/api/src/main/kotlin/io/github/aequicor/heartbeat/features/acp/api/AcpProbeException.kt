package io.github.aequicor.heartbeat.features.acp.api

import java.io.IOException

/** The message is suitable for the check log and excludes agent output, arguments and environment values. */
public class AcpProbeException(public val started: Boolean, message: String, cause: Throwable) :
    IOException(message, cause)
