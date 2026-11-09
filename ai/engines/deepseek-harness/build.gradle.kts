plugins {
    id("heartbeat-jvm")
    id("dev.zacsweers.metro")
}

dependencies {
    implementation(project(":ai:engine:api"))
    implementation(project(":ai:engine:acp"))
    implementation(project(":di:api"))
    implementation(libs.metro.runtime)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
}
