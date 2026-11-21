package io.github.aequicor.heartbeat.features.aistudio.data.exception

internal class ProjectNameAlreadyExistsException(val immutableName: String) :
    Exception("A project with that name already exists")
