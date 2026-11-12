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
    ":core:preferences:api",
    ":core:preferences:datastore",
    ":core:preferences:sqlite",
)
include(":storage:database")
include(
    ":designsystem:foundation",
    ":designsystem:sandbox",
    ":designsystem:consumer",
    ":designsystem:compose:theme",
    ":designsystem:compose:resources",
    ":designsystem:compose:components",
    ":designsystem:compose:desktop",
)
include(
    ":navigation:api",
    ":navigation:decompose",
    ":navigation:decompose-compose",
)
include(
    ":features:ai-engine:api",
    ":features:ai-engine-acp-adapter:impl",
    ":features:ai-engine-facade:api",
    ":features:ai-engine-facade:impl",
    ":features:engines:dsh",
    ":features:ai-studio:api",
    ":features:ai-studio:impl",
    ":features:ai-studio:presentation:decompose",
    ":features:ai-studio:ui:compose",
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
