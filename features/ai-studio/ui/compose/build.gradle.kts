plugins {
    id("heartbeat-desktop-wheel")
    id("heartbeat-kmp-compose")
    id("heartbeat-metro")
}

kotlin {
    android { androidResources.enable = true }
    sourceSets.jvmMain.dependencies {
        compileOnly(libs.jcef.api)
        implementation(libs.filekit.dialogs.compose)
    }
    sourceSets.androidMain.dependencies {
        implementation(libs.androidx.activity.compose)
    }
    sourceSets.commonMain.dependencies {
        implementation(compose.components.resources)
        implementation(project(":designsystem:compose:components"))
        implementation(project(":features:ai-studio:api"))
        implementation(project(":features:ai-studio:impl"))
        implementation(project(":features:ai-studio:presentation:decompose"))
        implementation(project(":features:settings:api"))
        implementation(project(":navigation:decompose-compose"))
        implementation(project(":di:api"))
        implementation(libs.kotlinx.coroutines.core)
    }
    sourceSets.jvmTest.dependencies {
        implementation(project(path = ":features:ai-studio:impl", configuration = "jvmTestFixtures"))
        implementation(project(":core:preferences:api"))
        implementation(libs.compose.ui)
        implementation(compose.desktop.currentOs)
        implementation(libs.kotlinx.coroutines.test)
    }
}

compose.resources {
    packageOfResClass = "io.github.aequicor.heartbeat.features.aistudio.ui.resources"
}
