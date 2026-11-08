plugins {
    id("heartbeat-compose-designsystem")
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(compose.components.resources)
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "io.github.aequicor.heartbeat.designsystem.resources"
}

// The module has no handwritten Kotlin: it bundles vector assets, and the resource accessors are
// generated. Analysing generated code adds no value, so detekt tasks of this module are disabled.
tasks.matching { it.name.startsWith("detekt") }.configureEach {
    enabled = false
}