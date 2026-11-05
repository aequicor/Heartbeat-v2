plugins {
    kotlin("multiplatform")
}

kotlin {
    macosArm64 {
        binaries.executable {
            baseName = "heartbeat"
        }
    }
}
