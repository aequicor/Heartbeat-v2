---
name: kotlin-native-interop
description: >
  Explains the bidirectional Kotlin/Native interop bridge: how native C, Objective-C,
  and Swift code calls into Kotlin and how Kotlin calls into native code. Covers cinterop
  bindings (.def files), the kotlinx.cinterop API (CPointer, CValue, CValuesRef, memScoped,
  nativeHeap), callbacks via staticCFunction, StableRef for carrying Kotlin objects through
  native code, @CName and @ObjCName export, framework header generation, @Throws and
  suspend->completion-handler mapping, and the runtime trampolines that cross the boundary.
  Use when writing or reviewing Kotlin/Native interop code, platform.* imports, cinterop
  .def files, native callbacks or function pointers, or diagnosing crashes, leaks, and
  "InvalidMutabilityException"/"Symbol not found" errors at the native boundary.
license: Apache-2.0
metadata:
  author: Heartbeat
  version: "1.0.0"
---

# Kotlin/Native Interop — The Bidirectional Bridge

Use this skill when a task touches the boundary between Kotlin code and native code
(C, Objective-C, or Swift) on a Kotlin/Native target (iOS, macOS, or a native desktop
library). Companion skills: `kotlin-tooling-native-build-performance` (build/link speed),
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
2. **Kotlin → Native (outbound):** the compiler emits native *entry points* — a C symbol
   for `@CName` functions, an Objective-C header for a framework, or a C function pointer
   for `staticCFunction` — that convert native ABI arguments into Kotlin values, run the
   Kotlin body, and convert the result back.

Keep the two directions explicit in any design: who owns the memory, who owns the object
lifetime, and which side may throw.

## Direction 1 — Native → Kotlin (cinterop)

### Setup

1. Write a `.def` file listing the headers to import (and package, linker options,
   `strictEnums`, `noStringConversion`, `disableDesignatedInitializerChecks`, custom
   declarations).
2. Register it in Gradle so the `cinterop<target>` task runs before compilation.
3. The generated declarations live in the configured package (system frameworks in
   `platform.*`, e.g. `platform.Foundation`, `platform.UIKit`, `platform.posix`).

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

### To C

- `@CName("symbol")` (package `kotlin.native`, Kotlin 1.5+) on a **top-level** function
  exports it as a C function with that symbol name, callable from C/C++.
- Build the module as a static/dynamic library (or executable) and link it; declare the
  symbol in a header manually or generate it.
- Return a C function pointer to C with `staticCFunction`; hand C a Kotlin object through
  `StableRef.create(...).asCPointer()`.

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
- **Native → Kotlin call (`@CName`, ObjC method, `staticCFunction` pointer):** the
  generated entry point is a native trampoline that attaches/initializes the runtime
  thread state, converts ABI arguments into Kotlin values, runs the body, converts the
  result back, and converts/terminates exceptions per `@Throws`. The Kotlin/Native runtime
  manages the thread's association with the GC.
- **`StableRef`** is the runtime's handle (a GC-rooted reference) exposed as an opaque
  pointer; native code holds it, `get()`/`dispose()` resolve/release it. Leaking a
  `StableRef` leaks the Kotlin object.
- **ObjC ARC integration:** Kotlin's tracing GC cooperates with Objective-C ARC for
  objects crossing the ObjC boundary (strong/weak references, autorelease pools). Prefer
  the generated framework over manual `autoreleasepool` unless performance demands it.
- **Symbol linking:** using an ObjC class in Kotlin marks it *strongly linked*, so a
  missing symbol can crash at launch even if never called — wrap conditional class usage
  in a Swift/ObjC availability check.

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

## Workflow

1. Determine the direction(s): Kotlin calls native, native calls Kotlin, or both.
2. Inbound: write the `.def` file, register cinterop, then use the mapped types above;
   prefer platform libraries (`platform.*`) over hand-written `.def` where they exist.
3. Outbound to C: `@CName` + a static/dynamic library binary, `staticCFunction` and
   `StableRef` for callbacks/state.
4. Outbound to ObjC/Swift: declare the `framework` binary, apply `@ObjCName`/`@Throws`/
   `@HiddenFromObjC` where the API needs shaping, and verify the generated header names.
5. For each crossing, state the ownership: memory lifetime (scope vs heap) and object
   lifetime (`StableRef` dispose site). Then write the smallest compile-and-run check.
