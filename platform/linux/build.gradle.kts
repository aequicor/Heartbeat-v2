plugins {
    kotlin("multiplatform")
}

kotlin {
    linuxX64 {
        binaries.executable {
            baseName = "heartbeat"
        }
    }
}
