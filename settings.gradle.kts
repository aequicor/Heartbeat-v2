pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Heartbeat"
include(":platform:android", ":platform:desktop")
include(":di:api")
include(":core:platform-api", ":core:network", ":core:database", ":core:filesystem")
include(
    ":designsystem:compose:theme",
    ":designsystem:compose:resources",
    ":designsystem:compose:components",
    ":designsystem:compose:desktop",
)
include(
    ":features:editor:api",
    ":features:editor:impl",
    ":features:editor:presentation:decompose",
    ":features:editor:ui:compose",
)
