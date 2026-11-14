import heartbeat.KmpDefaults

// JVM-only implementation modules: AI engine adapters and agent providers that rely on JVM
// libraries such as child processes and stdio transports. Universal contracts and logic stay in
// heartbeat-kmp-* modules.

plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain {
        languageVersion = KmpDefaults.JAVA_LANGUAGE_VERSION
        vendor = KmpDefaults.JVM_VENDOR
    }
}
