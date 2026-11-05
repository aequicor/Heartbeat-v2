---
name: kotlin-native-interop
description: >
  Explains the bidirectional Kotlin/Native interop bridge on every native target: how C,
  Objective-C, and Swift code calls into Kotlin and how Kotlin calls into native code.
  Covers cinterop bindings (.def files), platform libraries (platform.posix, platform.linux,
  platform.windows, platform.Foundation), the kotlinx.cinterop API (CPointer, CValue,
  CValuesRef, memScoped, nativeHeap), callbacks via staticCFunction, StableRef, @CName and
  @ObjCName export, shared/static library building (.so/.dylib/.dll plus the generated
  *_api.h symbols gateway), framework header generation, @Throws and
  suspend->completion-handler mapping, the runtime trampolines that cross the boundary, and
  memory/CPU management (the tracing GC, native-vs-GC heaps, coroutine dispatchers, and
  thread affinity). Use when writing or reviewing Kotlin/Native interop code on Windows,
  Linux, macOS, or iOS, platform.* imports, cinterop .def files, native callbacks or
  function pointers, or diagnosing crashes, leaks, GC pauses, and
  "InvalidMutabilityException"/"Symbol not found" errors at the native boundary.
license: Apache-2.0
metadata:
  author: Heartbeat
  version: "1.2.0"
---

# Kotlin/Native Interop — The Bidirectional Bridge

Use this skill when a task touches the boundary between Kotlin code and native code
(C, Objective-C, or Swift) on any Kotlin/Native target: iOS/macOS via the Apple framework
and ObjC path, or Windows/Linux/macOS via the native desktop targets (`mingwX64`,
`linuxX64`/`linuxArm64`, `macosArm64`) and the plain C ABI. Companion skills:
`kotlin-tooling-native-build-performance` (build/link speed),
`kotlin-tooling-cocoapods-spm-migration` (dependency packaging for iOS).

## The mental model

Kotlin/Native does not run on a JVM. The compiler lowers Kotlin to LLVM IR and emits a
native binary (executable, static/dynamic library, or Apple framework) that links the
Kotlin/Native runtime — a native runtime providing the tracing GC, exception handling,
reflection, and interop support.

Because both sides are ultimately native code on the same ABI, the "bridge" is not a
per-call marshalling layer. It is two compile-time artifacts:

1. **Native → Kotlin (inbound):** `cinterop` generates Kotlin *bindings* (stubs) from
   C/Objective-C headers described in a `.def` file. Calling a stub is compiled directly
   into a native call with ABI-correct argument placement; `CPointer`/`CValue` are thin,
   zero-copy views over native memory.
2. **Kotlin → Native (outbound):** the compiler emits native *entry points* — a generated
   `<prefix>_api.h` / `<prefix>_symbols()` gateway for shared/static libraries, a C symbol
   for `@CName` functions, an Objective-C header for a framework, or a C function pointer
   for `staticCFunction` — that convert native ABI arguments into Kotlin values, run the
   Kotlin body, and convert the result back.

Keep the two directions explicit in any design: who owns the memory, who owns the object
lifetime, and which side may throw.

## Native desktop targets and platform libraries (Windows / Linux / macOS)

The C bridge is the same mechanism on every target — only the target name, the OS platform
library, and the output artifact differ. Pick the target first:

| OS | Gradle target | Dynamic-lib artifact | Platform libraries |
| --- | --- | --- | --- |
| Windows | `mingwX64` (MinGW, Tier 3) | `libX.dll` + `libX.def` | `platform.posix`, `platform.windows` (Win32) |
| Linux | `linuxX64`, `linuxArm64` (Tier 2) | `libX.so` | `platform.posix`, `platform.linux` |
| macOS | `macosArm64` (Tier 1); `macosX64` deprecated | `libX.dylib` | `platform.posix`, `platform.Foundation`/`objc` |

- By default a target compiles only to a `.klib`, consumable by Kotlin/Native itself but
  **not** by native code. Declare a final binary to get a native artifact.
- `binaries { }` factories: `executable`, `sharedLib` (dynamic), `staticLib`, `test` — on
  every native target; `framework` — Apple targets only. Each factory emits a `DEBUG` and
  a `RELEASE` binary unless you restrict the build types.
- Host limits: Apple targets build **only on a macOS host**; Linux and Windows hosts build
  Linux/Windows targets and can produce `.klib` for Apple targets (only without cinterop
  dependencies).
- `platform.posix` exists for all UNIX **and** Windows targets (not Wasm); its contents
  differ per OS. Win32 lives in `platform.windows`, Linux-specific calls in
  `platform.linux`, Apple frameworks in `platform.Foundation`/`platform.UIKit`/`objc`.
- Consuming a third-party C library on desktop is the same cinterop flow as Direction 1;
  only the `.def` `linkerOpts`/`compilerOpts` and the installed `.so`/`.a`/`.dll`/`.lib`
  differ per OS. On Windows the `.def` library name may need the `lib` prefix explicitly.

## Direction 1 — Native → Kotlin (cinterop)

### Setup

1. Write a `.def` file listing the headers to import (and package, linker options,
   `strictEnums`, `noStringConversion`, `disableDesignatedInitializerChecks`, custom
   declarations).
2. Register it in Gradle so the `cinterop<target>` task runs before compilation.
3. The generated declarations live in the configured package (system frameworks in
   `platform.*`, e.g. `platform.Foundation`, `platform.UIKit`, `platform.posix`,
   `platform.windows`).

C/ObjC import is in Beta: generated declarations carry `@ExperimentalForeignApi`, so
interop call sites need `@OptIn(ExperimentalForeignApi::class)` (package
`kotlinx.cinterop`). Platform libraries shipped with Kotlin/Native require the opt-in
only for some APIs.

### Type mapping cheat sheet

| C / Objective-C | Kotlin |
| --- | --- |
| `int8_t` … `int64_t`, `uint*`, `float`, `double` | `Byte`/`UByte` … `Long`/`ULong`, `Float`, `Double` (same width) |
| `T*`, `T[]` | `CPointer<T>?` (null is `null`) |
| `void*` | `COpaquePointer` (supertype of all pointers) |
| `struct S`, `union S` | `S` (an *lvalue* type; fields via dot notation) |
| lvalue of any type | `${type}Var` (e.g. `IntVar`, `CPointerVar<ByteVar>`), with mutable `.value` |
| struct passed/returned **by value** | `CValue<S>` (opaque; use `readValue`/`useContents`) |
| `typedef` | `typealias` |
| constant macro | Kotlin `val` property |
| `char*` / `const char*` | `String` (auto-converted); raw pointer with `noStringConversion` |
| Objective-C `@protocol Foo` | `interface FooProtocol` |
| `BOOL` / ObjC classes / `NSString` … | `Boolean` / classes / mapped via `platform.*` |

Pointers: `CPointer<T>.pointed` gives the lvalue, `.ptr` the reverse; `.reinterpret<T>()`
casts (unsafe); `.toLong()`/`.toCPointer<T>()` are unsafe pointer↔integer casts.
`void*` callbacks carry user data as `COpaquePointer`.

### Memory allocation

- `nativeHeap` — `malloc`-backed placement; `alloc`/`allocArray`, manual `free`.
- `memScoped { }` — temporary placement freed on scope exit; the default for short-lived
  buffers and out-parameters.
- Pointer parameters map to `CValuesRef<T>`: pass a `CPointer<T>` as-is, or a value
  sequence (`cValuesOf(1,2,3)`, `array.toCValues()`) that lives only until the call returns.
- `.cstr` / `.wcstr` produce a C string `CValuesRef`; get a real pointer with
  `.cstr.getPointer(nativeHeap)` or `.cstr.ptr` inside `memScoped`. C strings are UTF-8.

### Structs by value

`CValue<T>` is opaque — fields are not accessible directly. Build/read with
`cValue { field = ... }`, `alloc<T>()` + `readValue()`, or `.useContents { ... }`;
`.copy { ... }` makes a modified copy; `CValues<T>.placeTo(scope)` materializes a pointer.

### Callbacks and user data (the round-trip through C)

`staticCFunction(::kotlinFunction)` or `staticCFunction { ... }` converts a Kotlin
function/lambda into a `CPointer<CFunction<...>>`. **The lambda must not capture any
state** — only top-level/object functions or capture-free lambdas qualify.

To let a C API call back into a *specific* Kotlin object, wrap it in a `StableRef`:

```kotlin
val ref = StableRef.create(obj)         // pin obj so the GC keeps it alive
val userData: COpaquePointer = ref.asCPointer()
// ... pass userData to C ...
val back = userData.asStableRef<MyClass>()
val obj  = back.get()                    // recover the original object
ref.dispose()                            // release; REQUIRED or it leaks
```

A disposed `StableRef` is invalid — never unwrap it again.

### Pinning

Pass Kotlin array/object memory to native without copying via `usePinned` or `refTo`:

```kotlin
buffer.usePinned { pinned -> recv(fd, pinned.addressOf(0), size.convert(), 0) }
```

`pinned.addressOf(index)` / `buffer.refTo(index)` pin only for the duration of the call;
the memory must not outlive that window.

### Portability and forward declarations

- `size_t`/`long` and other platform-dependent integers: use `.convert<T>()` (Kotlin has
  no implicit integer casts).
- Forward-declared C structs / ObjC classes: import `cnames.structs.X`,
  `objcnames.classes.X`, `objcnames.protocols.X` and cast between the two cinterop views.

## Direction 2 — Kotlin → Native

### To C (all desktop targets)

Two complementary ways to expose Kotlin to C. Use the automatic gateway for a full
library API; use `@CName` for a small, hand-picked set of C symbols.

**1. Automatic `<prefix>_api.h` gateway (recommended for shared/static libraries).**
Declare `binaries { sharedLib { baseName = "native" } }` and build
`linkDebugShared<target>` / `linkReleaseShared<target>` (or `linkNative`). The compiler
emits, next to the library, a C header `<prefix>_api.h` (e.g. `libnative_api.h`) whose
single entry point is `<prefix>_symbols()` — a function returning a pointer to an
`<prefix>_ExportedSymbols` struct of function pointers:

- Kotlin objects are C handles: `kref_*` structs wrapping a `void*` `pinned` field. C must
  call `DisposeStablePointer(handle.pinned)` and `DisposeString(char*)` to release them —
  the tracing GC does **not** collect objects C still holds.
- Primitives map to fixed C types in the header (`KInt`→`int`, `KLong`→`long long`,
  `KChar`→`unsigned short`, `KBoolean`→`bool`/`_Bool`, `KNativePtr`→`void*`).
- Members become function pointers taking the `thiz` handle first; `object` singletons get
  an `_instance()` accessor; properties become `get_`/`set_` functions.
- Host the returned `*_symbols()` pointer per thread if you call it from several threads.

**2. `@CName("symbol")` direct export.** `@CName` (package `kotlin.native`, Kotlin 1.5+)
on a **top-level** function emits a real C symbol with that name, callable from C/C++
through `extern` declarations you write yourself. Use it to curate a stable C ABI.

For both: return a C function pointer with `staticCFunction` and hand C a Kotlin object
through `StableRef.create(...).asCPointer()`.

**Linking and consuming per OS:**

- Linux: `gcc main.c libX.so`; the loader needs `LD_LIBRARY_PATH` to include the lib dir.
- macOS: `clang main.c libX.dylib`.
- Windows (MinGW toolchain): the build also emits a `libX.def`. Generate an import library
  (`lib /def:libX.def /out:libX.lib`, then `cl.exe main.c libX.lib` with MSVC), or load at
  runtime with `LoadLibrary`/`GetProcAddress`. The C client needs MSVC (or equivalent);
  the Kotlin toolchain itself uses MinGW.

### To Objective-C / Swift (Apple framework)

Compile into a **framework**; the compiler generates an Objective-C header that Swift
consumes. Key rules and controls:

- Name translation: a framework name prefix is prepended to Kotlin class/interface names;
  top-level functions/properties are exposed as members of `<File>Kt` classes.
- `@ObjCName` (e.g. `@ObjCName(swiftName = "MySwiftArray")`) renames a class/member in the
  exported ObjC/Swift API without renaming the Kotlin declaration. Add
  `@OptIn(ExperimentalObjCName::class)` if the compiler asks.
- `@Throws(IOException::class)` on a function → `NSError*`/`throws` in the exported API.
  Kotlin exceptions **not** listed in `@Throws` terminate the program when they cross the
  boundary; `suspend` functions always take an `NSError`/completion handler and propagate
  `CancellationException`.
- `suspend fun` → ObjC completion handler; since Swift 5.5 also callable as `async`
  (experimental).
- `@HiddenFromObjC` hides a declaration from ObjC/Swift while keeping it visible to Kotlin
  (use instead of `internal` when only the ObjC side must not see it).
- `@ShouldRefineInSwift` marks a declaration `swift_private` (gets a `__` prefix) so you
  wrap it in Swift for a friendlier API.
- `@OverrideInit constructor(coder: NSCoder)` overrides an imported initializer when
  subclassing (`UIViewController` etc.); `@ObjCSignatureOverride` disambiguates clashing
  overloads; `@Suppress("CAST_NEVER_SUCCEEDS")` for ObjC↔Kotlin `as` casts.
- Singletons: `object Foo` / `companion object` are accessed as `Foo.shared` /
  `Foo.companion` from Swift.
- Mappings: `class→@interface`, `interface→@protocol`, `enum class→class`, `List→NSArray`,
  `Map→NSDictionary`, function types→blocks/closures; `equals()→isEquals:`,
  `hashCode()→hash`, `toString()→description`.

Performance gotcha: Kotlin collections and `String` crossing to Swift are copied twice
(Kotlin → ObjC → Swift). Cast to `NSArray`/`NSDictionary`/`NSString` on the Swift side to
skip the second copy.

### Swift export (experimental)

Newer Kotlin can generate a Swift module interface directly (direct Swift export) instead
of routing through Objective-C. Prefer Objective-C interop for stability; mention Swift
export only when the task or build already uses it.

## The runtime bridge (what actually crosses the boundary)

- **Kotlin → native call (cinterop stub):** compiled straight to a native call. Primitives
  use the C ABI directly; `CPointer`/`CValue` are views, so there is no boxing and no
  interpreter. Cost is a plain function call.
- **Native → Kotlin call (`@CName`, ObjC method, `staticCFunction` pointer, `_symbols()`
  gateway):** the generated entry point is a native trampoline that attaches/initializes
  the runtime thread state, converts ABI arguments into Kotlin values, runs the body,
  converts the result back, and converts/terminates exceptions per `@Throws`. The
  Kotlin/Native runtime manages the thread's association with the GC.
- **`StableRef`** is the runtime's handle (a GC-rooted reference) exposed as an opaque
  pointer; native code holds it, `get()`/`dispose()` resolve/release it. Leaking a
  `StableRef` leaks the Kotlin object. On the C-gateway side the equivalent release calls
  are `DisposeStablePointer`/`DisposeString`.
- **ObjC ARC integration:** Kotlin's tracing GC cooperates with Objective-C ARC for
  objects crossing the ObjC boundary (strong/weak references, autorelease pools). Prefer
  the generated framework over manual `autoreleasepool` unless performance demands it.
- **Symbol linking:** using an ObjC class in Kotlin marks it *strongly linked*, so a
  missing symbol can crash at launch even if never called — wrap conditional class usage
  in a Swift/ObjC availability check.

## Memory and CPU management

### Memory

- **New memory manager (default, stable).** Objects live in one shared heap reachable from
  any thread — there is no freezing and no `InvalidMutabilityException`. The legacy
  `Worker`/`TransferMode`/`.freeze()` model is gone; use coroutines and plain shared state.
- **Tracing GC.** A concurrent mark-and-sweep collector runs on a separate thread; the mark
  phase is parallelized across app/GC threads, so pauses are short but consume CPU. Force a
  cycle with `kotlin.native.internal.GC.collect()`; watch it via
  `-Xruntime-logs=gc=info` (stderr) and check leaks with `GC.lastGCInfo()`.
- **Tuning knobs** (`gradle.properties`): `kotlin.native.binary.gc=pmcs` (fallback parallel
  mark) or `=noop` (disable — tests/short-lived programs only), `gcMarkSingleThreaded=true`,
  `pagedAllocator=false`, `latin1Strings=true`.
- **Two heaps at the boundary.** Kotlin objects live on the GC heap; native buffers live on
  the native heap (`memScoped`/`nativeHeap`). Never let a pointer to one outlive the other's
  lifetime (see Direction 1).
- **Ownership contract.** `StableRef` (Kotlin side) and `kref_*` +
  `DisposeStablePointer`/`DisposeString` (C gateway) are what let native code hold Kotlin
  objects; release exactly once or the GC can never reclaim them.
- **ObjC ARC.** Kotlin's GC cooperates with Objective-C ARC; wrap tight loops that create
  many autoreleased ObjC objects in `autoreleasepool { }`.

### CPU and threads

- **Coroutines, not Worker.** Use `Dispatchers.Default` (background pool) and
  `Dispatchers.Main` (UI thread); `newSingleThreadContext`/`newFixedThreadPoolContext` give
  explicit pools. UI on iOS/Android must run on the main thread (`Dispatchers.Main`).
- **Thread affinity across the bridge.** A native callback runs on the *caller's* native
  thread; the trampoline attaches that thread to the runtime but does not move the work —
  hop to `Dispatchers.Main`/`Default` explicitly when the callee must run elsewhere.
- **Don't block the UI thread.** A long/blocking native call or `GC.collect()` on the main
  thread stalls rendering; offload to a background dispatcher and switch back.
- **Per-thread state.** Use `@ThreadLocal` (`kotlin.native.concurrent`) for thread-local
  objects; a plain `object` singleton is shared across threads.

## Invariants and pitfalls (check these first when debugging)

1. `staticCFunction` lambda capturing state → compile error. Use `StableRef` for state.
2. `StableRef` not disposed → object leak; disposed and reused → crash.
3. cinterop call sites without `@OptIn(ExperimentalForeignApi::class)` → compile error.
4. Buffer passed to native via `usePinned`/`refTo` held beyond the call → use-after-free.
5. `memScoped` pointer escaping its scope → dangling pointer; use `nativeHeap` for longer
   lifetimes.
6. C `int`/`long`/`size_t` width mismatches → use `.convert<T>()`, never implicit casts.
7. Kotlin exception crossing to ObjC/Swift without `@Throws` → program termination.
8. ObjC `null`↔Kotlin `null`: generics default to nullable in the exported header unless
   bounded `T : Any`.
9. "Symbol not found" at launch on some OS versions → strong linking of an ObjC class.
10. `NSNumber` is not auto-converted to a Kotlin primitive — cast explicitly.
11. `.klib` is not consumable by native code — declare `sharedLib`/`staticLib`/`executable`.
12. Apple targets build only on a macOS host; `mingwX64`/`linuxX64` need the matching host
    toolchain.
13. C holds a `kref_*` handle and never calls `DisposeStablePointer`/`DisposeString` → the
    Kotlin object leaks; release exactly once.
14. `.freeze()`/`Worker`/`TransferMode` are gone in the new MM — use coroutines and
    shared-heap state.
15. A native callback runs on the caller's thread; marshal to `Dispatchers.Main`/`Default`
    instead of assuming the current thread.
16. Long native calls or `GC.collect()` on the main/UI thread → UI hitches; offload first.

## Workflow

1. Determine the direction(s) and the target OS (Windows/Linux/macOS/iOS) up front.
2. Pick the target (`mingwX64`/`linuxX64`/`linuxArm64`/`macosArm64`/iOS targets) and the
   platform libraries for that OS.
3. Inbound: write the `.def` file, register cinterop, then use the mapped types above;
   prefer `platform.*` over hand-written `.def` where they exist.
4. Outbound to C: declare `sharedLib`/`staticLib`/`executable`; use the generated
   `*_api.h` gateway for a library API or `@CName` for a curated C ABI; `staticCFunction`
   and `StableRef` for callbacks/state; on Windows generate the import lib or LoadLibrary.
5. Outbound to ObjC/Swift (Apple only): declare the `framework` binary, apply
   `@ObjCName`/`@Throws`/`@HiddenFromObjC` where the API needs shaping, verify the header.
6. For each crossing, state the ownership: memory lifetime (GC heap vs native heap), object
   lifetime (`StableRef` dispose / `DisposeStablePointer` site), and thread (which
   dispatcher/thread runs each side). Then write the smallest compile-and-run check.
