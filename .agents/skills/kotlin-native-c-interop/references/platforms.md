# Kotlin/Native host, target, and OS integration rules

## Target matrix and build constraints

| Target | OS / architecture | Notes |
| --- | --- | --- |
| `mingwX64` | Windows x86-64 | MinGW GNU ABI, not MSVC. |
| `linuxX64` | Linux x86-64 | GNU/Linux target. |
| `linuxArm64` | Linux aarch64 | Verify local or CI runner and native dependency builds. |
| `macosArm64` | macOS Apple Silicon | Build Apple final binaries on macOS with Xcode SDK. |
| `macosX64` | macOS Intel | Deprecated in recent Kotlin versions; opt in only with explicit product need. |

Kotlin/Native can cross-compile some targets, but Apple final binary builds and Apple cinterop dependencies require a macOS host. Running tests requires the suitable native OS/architecture. Build matrices must not pretend that a Windows or Linux CI job validated a macOS GUI binary.

## Windows / Win32

- First try bundled `platform.windows.*` bindings (confirm available declarations for this Kotlin version). Custom `.def` is for missing/custom APIs.
- Prefer `CreateWindowExW`, `RegisterClassExW`, `DefWindowProcW`, and other explicit wide Win32 names over relying on `UNICODE` macro selection. Use UTF-16 wide strings, e.g., `"Hello".wcstr.ptr` within `memScoped`, with pointer types checked against generated APIs.
- `HWND`, `HINSTANCE`, `HANDLE`, `HDC`, `HBITMAP`, and other handles have different ownership/release functions. Examples: `DestroyWindow`, `CloseHandle`, `ReleaseDC`, `DeleteObject` apply only to the documented resource type/ownership.
- Implement the Win32 message loop and window procedure with correct lifetime, thread, error, and `WM_NCDESTROY` cleanup. `staticCFunction` must match the emitted `WNDPROC` signature; check `CALLBACK` conventions. GUI work belongs to the window's owning thread.
- Modern/custom title bars are separate from C interop: preserve native resize, hit-test (`WM_NCHITTEST`), window state, snap/system menu, DPI changes (`WM_DPICHANGED`), focus, accessibility, and DWM integration. Do not replace functioning native caption buttons without an explicit product requirement.
- Link correct Windows system libraries only when needed. Bundled platform libraries may supply transitive linker options; for custom wrappers, typical linker imports include `-luser32`, `-lgdi32`, `-ldwmapi`, and `-lcomctl32` depending on actual calls. Never add libraries speculatively.
- `.dll` is runtime-loaded; MinGW `.a` import libraries are often appropriate for linking. An MSVC `.lib` may be incompatible with a GNU toolchain; confirm file format, symbol decoration, and actual import mechanism. Avoid mixing C++ runtime ABIs across a C++/C boundary.
- Use native diagnostics: PowerShell/Windows terminal, `dumpbin` when installed, `llvm-objdump`/`objdump`, and dependency inspection. Account for PowerShell text encodings and paths with spaces. Do not run POSIX-specific build commands unchanged on Windows.

## Linux

- First use `platform.posix.*` for standardized C/POSIX APIs; OS and libc variations remain possible.
- For third-party libs, resolve include and link flags via the *target architecture's* `pkg-config` metadata where appropriate. Check `pkg-config --cflags --libs library-name`; propagate only actually needed flags.
- Distinguish system and desktop protocols: X11 and Wayland are different stacks. Wayland clients often require generated protocol bindings/shims and a compositor; Xlib requires an X server. Do not assume XWayland, `$DISPLAY`, or `$WAYLAND_DISPLAY`.
- In native GUI integrations, run the toolkit's event loop on its designated thread. Avoid blocking it with synchronous Kotlin work. If using SDL/GLFW/GTK, match the library's native install and deployment requirements.
- For linking errors inspect `nm`, `readelf`, `objdump`, and library architecture; for runtime loading inspect `ldd` (trusted binaries only), `readelf -d`, RPATH/RUNPATH, and packaging.
- Avoid relying on globally installed `.so` versions in release builds unless documented and tested.

## macOS

- Apple framework/ObjC imports may already exist as `platform.Foundation`, `platform.AppKit`, `platform.CoreFoundation`, etc.; check actual packages and bindings.
- AppKit/Cocoa is Objective-C, not a plain C API. For custom frameworks, `language = Objective-C`, `modules = YourModule`, and/or appropriate Clang framework options may be needed; prefer built-in bindings where they exist.
- A graphical AppKit application must follow the platform's main-thread/event-loop rules. Do not call AppKit from arbitrary coroutines/native threads.
- For C system APIs, use `platform.posix`; for `CFTypeRef` ownership inspect `Create`/`Copy` naming rules and actual Core Foundation documentation.
- `linkerOpts` may use `-framework FrameworkName` and relevant `-F` search paths for custom native frameworks; do not duplicate platform-library-managed flags without cause.
- Native binaries may need `.dylib`/framework embedding, `@rpath`/`@loader_path` planning, code signing, hardened runtime, and notarization for distribution. Keep developer-run and production packaging distinct.
- Inspect symbols and dependencies with `nm`, `otool -L`, `otool -l`, `file`, and `lipo` where appropriate.

## Shared cross-platform architecture

A Kotlin interface in `commonMain` is not itself a replacement for OS-specific native APIs. Implement OS adapters in native target source sets or target-specific modules; keep `CPointer` and raw native handles out of general business-domain models. Share `nativeMain` code only when APIs and actual headers/libraries are compatible.

For portable desktop UI/windowing, consider a native cross-platform C-facing library (e.g. SDL or GLFW) **only if the product requirements accept its behavior**; native window appearance, system controls, packaging, and accessibility still require verification per platform.

Sources:
- https://kotlinlang.org/docs/native-target-support.html
- https://kotlinlang.org/docs/native-platform-libs.html
- https://kotlinlang.org/docs/native-c-interop.html
