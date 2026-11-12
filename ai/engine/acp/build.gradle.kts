plugins {
    id("heartbeat-jvm")
}

dependencies {
    implementation(project(":ai:engine:api"))
    implementation(libs.agent.client.protocol)
    implementation(libs.kotlinx.coroutines.core)
    // The SDK logs through kotlin-logging, which needs the SLF4J API at runtime.
    implementation(libs.slf4j.api)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(kotlin("test"))
}
