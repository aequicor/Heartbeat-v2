plugins {
    kotlin("multiplatform")
}

kotlin {
    mingwX64 {
        binaries.executable {
            baseName = "heartbeat"
        }
    }
}
