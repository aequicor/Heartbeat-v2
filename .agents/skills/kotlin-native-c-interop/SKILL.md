---
name: kotlin-native-c-interop
description: Implement, debug, and review Kotlin/Native interoperability with C libraries and native operating-system APIs on Windows (mingwX64/Win32), Linux, and macOS. Use for cinterop .def files, Kotlin Multiplatform Gradle configuration, C headers and shims, pointers, native memory, strings, callbacks, ABI compatibility, native linking, and platform-specific desktop integrations.
compatibility: Kotlin/Native and Kotlin Multiplatform Gradle; native SDKs and C toolchains appropriate to each target.
metadata:
  version: "1.0.0"
---

# Kotlin/Native ↔ C interoperability

You are a Kotlin/Native and C ABI integration specialist. Your job is to deliver **working, memory-safe-at-the-boundary, platform-correct native integrations**, not merely code that appears plausible. Apply this skill to Kotlin/Native executables/libraries, Kotlin Multiplatform projects, and desktop OS integrations on Windows, Linux, and macOS.

## Activate this skill for

- Kotlin/Native calling C functions, consuming `.h` headers, `.a`/`.so`/`.dylib`/`.dll` binaries, or generating bindings with `cinterop`.
- Calling system APIs: Win32 on Windows, POSIX/X11/Wayland-related C libraries on Linux, POSIX/Core Foundation/Objective-C frameworks on macOS.
- C pointers, structs, unions, typedefs, buffers, strings, function pointers, callbacks, native threads, and memory ownership.
- Gradle/KMP target setup, linking, missing symbols, library loading, or target-specific build errors.

Do **not** conflate Kotlin/Native C interop with JVM JNI/JNA, and do **not** assume C++ APIs can be imported directly. For C++, create a carefully designed `extern "C"` facade compiled by a C++ compiler.

## Read references only when relevant

- `references/c-interop-patterns.md`: type mapping, pointers, strings, lifetimes, callbacks, C wrappers, and error handling.
- `references/platforms.md`: Windows/MinGW, Linux, macOS, GUI/event-loop restrictions, toolchains, library deployment, and cross-compilation.
- `references/diagnostics.md`: failure triage, build commands, ABI inspection, testing, and troubleshooting.
- `examples/`: compact C header + `.def` + Kotlin examples to copy and adapt.

## Mandatory workflow

1. **Inspect the repository before editing.** Identify Kotlin plugin version, Gradle wrapper, Kotlin targets, source sets, existing `.def` files, `nativeInterop/` directories, platform libraries, CMake/native build tasks, `AGENTS.md`, and project-specific rules. Prefer the existing build architecture. Do not overwrite working configuration.
2. **Identify the exact boundary.** Determine target OS/architecture/ABI, whether Kotlin calls C or C calls Kotlin, which symbols and headers are needed, and whether a prebuilt native library, C implementation, or system framework provides them. Record the library's allocation/free, callback, threading, and encoding contracts.
3. **Prefer Kotlin/Native platform libraries.** First inspect available packages such as `platform.posix`, target-specific `platform.*` packages, or Apple frameworks. These may avoid custom `.def` files entirely. Only introduce `cinterop` when a suitable binding is unavailable or insufficient.
4. **Read the real API.** Inspect installed headers, library documentation, generated Kotlin declarations, symbol tables, and official Kotlin documentation. Never invent signature mappings, structure layouts, constants, calling conventions, or library names.
5. **Choose the smallest maintainable implementation.** Keep shared logic in `commonMain` or `nativeMain` as appropriate; isolate OS-specific code in target/source-set implementations. Prefer a typed Kotlin façade over scattering raw C pointers through application code.
6. **Configure binding generation and linking separately.** A `.def` processes headers; it does not automatically compile an arbitrary `.c` implementation. Ensure required native binaries are built/obtained, linked, and packaged for each target.
7. **Prove correctness.** Run cinterop generation, target compilation, native link, and executable/tests on the available host. Test null/failure paths, lifetime cleanup, strings, and callback shutdown. If a target cannot run locally, mark it **not validated** and provide platform-specific CI verification.
8. **Report concisely.** State the modified files, native dependencies, target(s) actually validated, test commands and results, and any unverified assumptions. Do not claim success without a completed build/run.

## Core model: what cinterop does

- `cinterop` uses Clang to parse **C-compatible headers** and generates Kotlin declarations packaged in a `.klib`.
- The `.def` file selects headers and controls parsing, generated package, filters, linker flags, and other options.
- `compilerOpts`/`includeDirs` influence **header parsing**; `linkerOpts` and native build artifacts influence **linking**. Runtime loading of shared libraries is a separate concern.
- Kotlin/Native compiles Kotlin to a native binary via LLVM; it does **not** run on the JVM and does not use JNI for ordinary C calls.
- Generated C bindings may require `@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)`; scope the opt-in to the smallest practical area.

## Configure Kotlin Multiplatform Gradle

Add the following to a **KMP project that already applies the Kotlin Multiplatform plugin**. Keep the project's existing Kotlin version; do not replace it just to match documentation.

```kotlin
// build.gradle.kts
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

kotlin {
    mingwX64()       // Windows x64, MinGW ABI
    linuxX64()       // Linux x64
    linuxArm64()     // Linux ARM64
    macosArm64()     // Apple Silicon; requires macOS host to link
    // macosX64(): deprecated in recent Kotlin versions; enable only if needed.

    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.getByName("main") {
            cinterops {
                val mathBridge by creating {
                    definitionFile.set(
                        project.file("src/nativeInterop/cinterop/mathBridge.def")
                    )
                    includeDirs.allHeaders(
                        "src/nativeInterop/cinterop/include"
                    )
                }
            }
        }
        binaries {
            executable { entryPoint = "main" }
        }
    }
}
```

The sample assumes an executable module, a `main` entry point, and a common, C-compatible header. In real applications, **add only requested/supported targets** and reuse existing target definitions. Where different OS headers or compiler flags are required, configure `cinterops` per target instead of forcing one universal header.

Example `.def` for a simple header:

```properties
# src/nativeInterop/cinterop/mathBridge.def
headers = math_bridge.h
headerFilter = math_bridge.h
package = sample.nativebridge
```

Example header-only C function (no separate link library needed):

```c
// src/nativeInterop/cinterop/include/math_bridge.h
#ifndef MATH_BRIDGE_H
#define MATH_BRIDGE_H
static inline int native_add(int left, int right) { return left + right; }
#endif
```

```kotlin
// src/nativeMain/kotlin/Main.kt
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

import sample.nativebridge.native_add

fun main() {
    check(native_add(20, 22) == 42)
    println("C interop OK")
}
```

Inspect available tasks using `./gradlew tasks --all`. Typical native link tasks are `linkDebugExecutableLinuxX64`, `linkDebugExecutableMingwX64`, and `linkDebugExecutableMacosArm64`; only run a task on a host that can actually produce that binary. For native libraries without executables, use the appropriate KLIB/framework/shared-library tasks instead.

For an externally compiled native library, extend the definition appropriately:

```properties
headers = example.h
headerFilter = example.h
package = sample.nativebridge
compilerOpts = -I/path/to/target/include
linkerOpts.linux = -L/path/to/linux/libs -lexample
linkerOpts.osx = -L/path/to/macos/libs -lexample
linkerOpts.mingw_x64 = -L/path/to/mingw/libs -lexample
```

**Treat these `/path/to/...` paths as placeholders.** Do not copy hard-coded machine-specific paths into the repository. Prefer reproducible, target-specific Gradle configuration or tracked dependency resolution. Check the Kotlin version's exact accepted target suffixes, and verify the library architecture and ABI.

## Type and lifetime rules

| C concept | Kotlin/Native interop | Critical rule |
| --- | --- | --- |
| `int`, `uint32_t`, `size_t`, `long` | Fixed-width Kotlin integral types or target-specific aliases | Do not assume `long` has the same width on Windows and Unix; use `.convert()` when appropriate. |
| `T*`, C arrays | `CPointer<T>?`, `CValuesRef<T>` | Nullable pointer is C `NULL`; check before dereferencing. |
| `void*` | `COpaquePointer?` | Cannot treat it as a Kotlin object reference directly. |
| `struct Foo*` | `CPointer<Foo>?` | Use `.pointed`/`.ptr`; field access requires valid live storage. |
| `struct Foo` by value | `CValue<Foo>` | Use `useContents`, `readValue`, or the generated API as appropriate. |
| `char*` / `const char*` | Raw byte pointers or sometimes Kotlin `String` | Check generated signatures, encoding, mutability, and ownership. |
| Function pointer | `CPointer<CFunction<...>>?` | Use `staticCFunction` only with non-capturing functions/lambdas. |

Use these patterns:

```kotlin
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
import kotlinx.cinterop.*

fun temporaryValue(): Int = memScoped {
    val value = alloc<IntVar>()
    value.value = 42
    // Pass value.ptr to a synchronous C API here.
    value.value
}

fun withNativeBuffer(size: Int, work: (CPointer<ByteVar>) -> Unit) {
    require(size > 0)
    val buffer = nativeHeap.allocArray<ByteVar>(size)
    try { work(buffer) } finally { nativeHeap.free(buffer) }
}
```

The `work` callback above must **not** retain `buffer` after the function returns. `memScoped` allocations are freed at the end of the scope; pointers must not escape. For C-owned memory, call **that library's documented release function**, not an arbitrary allocator's `free`.

For temporary pinned `ByteArray` data, use `usePinned { pinned -> ... pinned.addressOf(0) ... }`; ensure C finishes accessing the buffer before the pin is released. Do not pass an ephemeral `refTo()` buffer to an API that retains the pointer. Do not use `reinterpret` to hide a type error.

## Callbacks and asynchronous C APIs

- `staticCFunction` cannot capture Kotlin state; provide a top-level or non-capturing function matching the **exact native signature and calling convention**.
- If the C API accepts `void* userData`, wrap state with `StableRef.create(state).asCPointer()`; recover using `userData!!.asStableRef<State>().get()`.
- Keep the `StableRef` alive while callbacks may occur. Unregister or stop the native producer; **wait until in-flight callbacks are finished**; only then call `stableRef.dispose()`.
- Never permit Kotlin exceptions to unwind across a C callback boundary. Catch failures inside the callback and return an API-appropriate error value.
- Respect native thread affinity and re-entrancy; GUI APIs frequently require the UI thread. Avoid suspending a callback or capturing `MemScope` storage in asynchronous work.
- Read the complete callback example and lifecycle discussion in `references/c-interop-patterns.md` before implementing a retained callback.

## Platform rules

**Windows (`mingwX64`).** This target uses the MinGW/Windows GNU ABI, **not** a normal MSVC Kotlin/Native target. Reuse bundled Win32 bindings where possible. Prefer Unicode `...W` APIs and correctly sized UTF-16 buffers. Verify `WINAPI`/`CALLBACK` signatures, `HRESULT`/`GetLastError`, ownership of `HANDLE`/`HWND`, and thread-bound message loops. `.lib` files built only for MSVC are not automatically interchangeable with MinGW import/static libraries. See `references/platforms.md`.

**Linux (`linuxX64`, `linuxArm64`).** Use POSIX and system/library bindings. Check the chosen C library's pkg-config flags, shared-library ABI, deployment dependencies, and whether a desktop API requires X11 or Wayland support. Do not assume an X server or desktop session exists in CI.

**macOS (`macosArm64`; legacy `macosX64` when required).** Use macOS SDKs/Xcode on a macOS host for Apple binaries with C/Objective-C interop. Prefer provided Apple platform bindings; use Objective-C interop for AppKit/Cocoa rather than claiming they are plain C APIs. Observe ARC/Core Foundation ownership conventions, autorelease behavior, and main-thread UI requirements.

## Error handling and ABI checklist

Before considering an interop call complete, verify:

- Real header and implementation agree on symbol name, binary version, target triple, architecture, alignment, `sizeof`/signedness, and struct packing.
- The function's calling convention, callback signature, and variadic/macro behavior are supported; otherwise add a minimal C ABI wrapper.
- For each pointer, identify **owner**, **allocation source**, **required free/release**, **valid lifetime**, **nullability**, and whether the callee retains it.
- String encoding, lengths, null terminators, and possible embedded NULs are specified; Windows UTF-16 is not silently treated as UTF-8.
- Native error result and `errno`/`GetLastError` are checked immediately where required; don't silently discard failures.
- No Kotlin exceptions cross C callbacks, and all resources are released in `finally`/equivalent shutdown paths.
- Final artifact links and launches on the actual supported host; runtime `.dll`/`.so`/`.dylib` dependencies are packaged or resolved deliberately.

## Troubleshooting and learning protocol

If something fails, distinguish **(A)** header parsing/cinterop generation, **(B)** Kotlin compile/type mismatch, **(C)** native linking, **(D)** dynamic loading, and **(E)** runtime/ABI/lifetime bugs. Investigate the **first actionable error** instead of changing many flags at once. Consult `references/diagnostics.md` for exact checks.

If documentation appears to conflict with generated Kotlin declarations, prefer the **project's exact Kotlin compiler version + actual generated API**, then verify against the corresponding native header and upstream documentation. For unfamiliar or version-sensitive behavior, check current official docs before making changes. Record concise reusable root-cause lessons in project documentation **only when the project's rules authorize it**; never silently modify global agent instructions or create unnecessary one-off Python scripts.

## Sources of truth

1. [Kotlin/Native C interop](https://kotlinlang.org/docs/native-c-interop.html)
2. [Kotlin/Native `.def` properties](https://kotlinlang.org/docs/native-definition-file.html)
3. [Platform libraries](https://kotlinlang.org/docs/native-platform-libs.html)
4. [Supported Kotlin/Native hosts and targets](https://kotlinlang.org/docs/native-target-support.html)
5. [Kotlin Multiplatform Gradle DSL — cinterops](https://kotlinlang.org/docs/multiplatform/multiplatform-dsl-reference.html)
6. [C interop tutorial](https://kotlinlang.org/docs/native-app-with-c-and-libcurl.html)

Use platform-vendor SDK documentation and the actual C library's header/source as additional primary references.
