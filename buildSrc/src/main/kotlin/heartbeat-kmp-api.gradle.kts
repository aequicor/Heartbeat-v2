// Public contract modules: `features/*/api`, `shell/api` and `di/api`. They define the API that
// other modules consume, so they enable Kotlin's explicit API mode: every public declaration must
// state its visibility and every public property/function must state its type. This keeps the
// contract surface deliberate and lets `explicitApi` reject accidental public leaks.
plugins {
    id("heartbeat-kmp-universal")
}

kotlin {
    explicitApi()
}
