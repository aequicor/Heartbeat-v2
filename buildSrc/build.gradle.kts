plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
}

// The plugin classes are exported to every module, so module build scripts apply plugin ids
// without versions. detekt is compileOnly-dependent on the Kotlin and Android Gradle plugins,
// therefore both are required on this classpath as well.
dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.compose.gradle.plugin)
    implementation(libs.compose.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
}
