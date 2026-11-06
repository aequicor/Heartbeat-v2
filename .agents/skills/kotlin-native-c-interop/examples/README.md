# Minimal header-only Kotlin/Native cinterop example

For an existing Kotlin Multiplatform executable module:

1. Copy `math_bridge.h` to `src/nativeInterop/cinterop/include/math_bridge.h`.
2. Copy `mathBridge.def` to `src/nativeInterop/cinterop/mathBridge.def`.
3. Configure `mathBridge` in `build.gradle.kts` using the snippet in `../SKILL.md`, including `includeDirs.allHeaders`.
4. Copy `Main.kt` to `src/nativeMain/kotlin/Main.kt` (or integrate the call in an existing entry point).
5. Run the host-appropriate Gradle tasks from `./gradlew tasks --all`.

This is a minimal illustrative sample. The Kotlin/Native build was **not executed** in the artifact-generation environment. C header syntax can be checked with Clang separately.
