plugins {
    id("heartbeat-compose-designsystem")
}

kotlin {
    sourceSets.jvmMain.dependencies {
        // Deliberately only the kit: the compilation proves that Foundation and theme types reached
        // through the published API are visible without a direct Foundation dependency.
        implementation(project(":designsystem:compose:components"))
    }
}
