package io.github.aequicor.heartbeat.features.acp.api

import java.io.IOException

public class AcpProbeException(public val started: Boolean, message: String, cause: Throwable) :
    IOException(message, cause)
