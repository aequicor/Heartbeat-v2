plugins {
    id("heartbeat-jvm")
}

dependencies {
    implementation(project(":ai:engine:api"))
    implementation(project(":ai:engine:acp"))
}
