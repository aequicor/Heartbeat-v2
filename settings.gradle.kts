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
    ":designsystem:foundation",
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
    ":features:home:api",
    ":features:home:impl",
    ":features:home:presentation:decompose",
    ":features:home:ui:compose",
    ":features:settings:api",
    ":features:settings:impl",
    ":features:settings:presentation:decompose",
    ":features:settings:ui:compose",
    ":shell:api",
    ":shell:impl",
    ":shell:presentation:decompose",
    ":shell:ui:compose",
)
include(":architecture-tests")
