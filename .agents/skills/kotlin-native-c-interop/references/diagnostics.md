# Kotlin/Native C interop diagnostics and validation

Always reproduce the smallest failing target and record the host OS, target, Kotlin plugin version, Gradle wrapper version, toolchain, and native library architecture.

## First classify the failure

| Stage | Typical symptom | Investigate |
| --- | --- | --- |
| A. cinterop/Clang parsing | Missing header, unknown type, unsupported macro | Include paths, transitive headers, target defines, `headerFilter`, C vs C++ header syntax. |
| B. Kotlin compilation | Unresolved reference, type mismatch, opt-in | Generated package/signature, target source sets, `@ExperimentalForeignApi`, nullable pointers, integer width. |
| C. Native link | `undefined reference`, `ld: library not found` | Implementation actually compiled, `linkerOpts`, library path/order, target ABI, symbol visibility, import library. |
| D. Dynamic loader | DLL/SO/dylib not found at startup | Deployment, RPATH/RUNPATH, PATH, `@rpath`, dependencies and loader policies. |
| E. Runtime | Crash, stale pointer, garbled text, deadlock | Ownership, buffer length/encoding, struct packing, callback ABI, UI thread, re-entrancy. |

## Gradle

Start with the project wrapper, not an arbitrary system Gradle:

```bash
./gradlew tasks --all
./gradlew cinteropMathBridgeLinuxX64 --stacktrace --info
./gradlew linkDebugExecutableLinuxX64 --stacktrace --info
```

Task names depend on interop name, target, module, and project build setup; inspect `tasks --all` first. On Windows use `gradlew.bat`. If Kotlin/Native toolchain installation cannot be completed (network/offline policy), report this and stop short of declaring the target validated.

Avoid changing Gradle plugin versions or download sources as a speculative diagnostic fix. Prefer narrow reproducible changes to include/link configuration.

## Inspect binaries and exports

- Linux: `file path/to/libfoo.so`, `readelf -h`, `readelf -Ws`, `nm -D`, `ldd` on trusted binaries, `readelf -d`.
- macOS: `file path/to/libfoo.dylib`, `nm -gU`, `otool -L`, `lipo -info` for universal binaries.
- Windows: `dumpbin /headers` and `/exports` (when available), `llvm-objdump -p`, `objdump -p`, architecture/dependency inspection.
- If a C++ symbol appears mangled, add/verify `extern "C"` on the facade; do **not** force Kotlin to call a guessed mangled name.
- If linking fails, confirm that the `.def` imported declarations only and that an appropriate object/static/shared library with real definitions was built and added to the link.

## Header compatibility

- Check `#pragma pack`, `__attribute__((packed))`, `typedef`, enums, `const`, signedness, `long`, `wchar_t`, `size_t`, and platform macros.
- Generate bindings with the correct target's header environment and flags; a host-installed C library may be for a different ABI or version.
- For function-like macros or complex C preprocessor tricks, build a small C wrapper; never paste C++ declarations directly into a C `.def`.
- Keep a reproducible header set and document the native library version; don't hide a system mismatch by changing Kotlin pointer casts.

## Runtime/ownership tests

1. Ordinary successful call returns expected values.
2. Failure codes and null handles are handled without crashing.
3. UTF-8 and UTF-16 strings are tested with non-ASCII text.
4. Zero-length buffers and boundary sizes are checked.
5. Resource is released exactly once; repeated close paths are safe.
6. Callback can be invoked synchronously during registration, concurrently when permitted, and never accesses disposed `StableRef`.
7. UI-thread/event-loop assertions are checked where applicable.
8. Executable runs with the actual packaged dynamic libraries, not only developer-local environment variables.

## Reporting template

- **Boundary:** Kotlin calls `X` in native library `Y` (version, target ABI).
- **Changed:** `.def`, Gradle, C wrapper, Kotlin adapter (exact paths).
- **Ownership:** input borrowed; output owned by ...; freed by ...; callback registered/unregistered by ...
- **Validated:** exact command / OS / target / result.
- **Not validated:** precise targets or runtime conditions, and why.
- **Next diagnostic step:** only if unresolved; state the first actionable error.

Primary references: https://kotlinlang.org/docs/native-c-interop.html and https://kotlinlang.org/docs/native-definition-file.html
