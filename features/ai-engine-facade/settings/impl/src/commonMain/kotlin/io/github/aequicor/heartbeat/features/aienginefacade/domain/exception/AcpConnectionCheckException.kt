package io.github.aequicor.heartbeat.features.aienginefacade.domain.exception

internal class AcpConnectionCheckException(val started: Boolean, val diagnostic: String?, cause: Throwable) :
    Exception("Не удалась проверка ACP", cause)
