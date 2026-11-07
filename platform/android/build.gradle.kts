plugins {
    id("com.android.application")
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
