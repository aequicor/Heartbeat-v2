plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    // The composition root lives in the platform compilation (HB-ARCH-014), so the Metro
    // compiler plugin runs here and aggregates the feature contributions into AndroidGraph.
    id("dev.zacsweers.metro")
}

android {
    namespace = "io.github.aequicor.heartbeat"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.aequicor.heartbeat"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":di:api"))
    implementation(project(":designsystem:compose:theme"))
    implementation(project(":navigation:api"))
    implementation(project(":navigation:decompose"))
    implementation(project(":navigation:decompose-compose"))
    implementation(project(":shell:presentation:decompose"))
    implementation(project(":shell:ui:compose"))
    implementation(project(":features:ai-studio:api"))
    implementation(project(":features:ai-studio:impl"))
    implementation(project(":features:ai-studio:presentation:decompose"))
    implementation(project(":features:ai-studio:ui:compose"))
    implementation(project(":features:settings:api"))
    implementation(project(":features:settings:impl"))
    implementation(project(":features:settings:presentation:decompose"))
    implementation(project(":features:settings:ui:compose"))
    implementation(project(":di:api"))
    implementation(project(":core:database"))
    implementation(project(":core:preferences:api"))
    implementation(project(":core:preferences:datastore"))
    implementation(project(":storage:database"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.metro.runtime)
}
