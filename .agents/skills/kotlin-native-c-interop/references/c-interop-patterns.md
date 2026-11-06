# Kotlin/Native C interop patterns

This file gives practical rules for adapting real C APIs. Always inspect the generated Kotlin stubs for exact signatures; naming, enum mappings, signedness, and string conversion can vary.

## 1. C ABI first

Header-first design: prefer simple fixed-width C types and explicit ownership. For a C++ implementation, export a narrow C facade:

```cpp
// native_api.h (valid C header)
#pragma once
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif

typedef struct native_engine native_engine;
native_engine* native_engine_create(void);
int32_t native_engine_do_work(native_engine*, const uint8_t* bytes, uint32_t count);
void native_engine_destroy(native_engine*);

#ifdef __cplusplus
}
#endif
```

The implementation may use C++ internally, but its header is valid C, exposes no C++ classes/templates or `std::string`, does not propagate exceptions, and is compiled with a C++ compiler. Match target ABIs and runtime deployment.

When a header uses a function-like macro or unsupported variadic interface, expose a `static inline` function or separately compiled C wrapper with a stable signature. For example:

```properties
headers = native_api.h
headerFilter = native_api.h
package = app.nativeapi
---
static inline int native_wrap_flag(int value) {
    return SOME_C_MACRO(value);
}
```

## 2. Values, pointers, and structs

```kotlin
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
import kotlinx.cinterop.*

fun createIntPointer(): Int = memScoped {
    val result = alloc<IntVar>()
    result.value = 7
    val raw: CPointer<IntVar> = result.ptr
    raw.pointed.value
}

fun arrayExample(): Int = memScoped {
    val array = allocArray<IntVar>(3)
    array[0] = 10
    array[1] = 20
    array[2] = 30
    array[0] + array[1] + array[2]
}
```

C `struct Foo*` gives you a pointer to an lvalue; C `struct Foo` passed by value yields a `CValue<Foo>`. Use `useContents` for read-only field access or `.readValue()` when copying an lvalue into a value is appropriate. Never assume `CValue<T>` exposes mutable fields directly.

Use `size_t`, `ssize_t`, `long`, and OS handle types as emitted by cinterop; use `.convert()` with review, not blind `.toInt()` truncation. A 64-bit Windows `long` is normally 32 bits, unlike typical 64-bit Unix `long`.

## 3. Buffer ownership model

1. **Borrowed synchronously:** `memScoped`, `cstr.ptr`, and `usePinned` are suitable only if the C call finishes using the pointer before it returns.
2. **Retained by C:** allocate a native buffer with the required lifetime, or keep the Kotlin array pinned until C releases it, and define an explicit shutdown path. Prefer copying into C-owned memory over long-lived pinning where feasible.
3. **Owned by C:** free with the API-designated function (`free`, `CoTaskMemFree`, `LocalFree`, `CFRelease`, `library_destroy`, etc.), never based on the pointer's Kotlin type alone.
4. **Owned by Kotlin nativeHeap:** balance every allocation with `nativeHeap.free` in a `finally`, using only pointers allocated by that placement.
5. **String returned by C:** check if it is borrowed, static, allocated, or invalidated by the next call before calling `toKString()`.

Example temporary byte-array access:

```kotlin
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
fun consumeBytesSynchronously(data: ByteArray, consume: (CPointer<ByteVar>, Int) -> Unit) {
    if (data.isEmpty()) return
    data.usePinned { pinned ->
        consume(pinned.addressOf(0), data.size)
    }
}
```

`consume` must complete synchronously and not store the pointer. `ByteArray.refTo(0)` pins for the duration of a synchronous C call, but is not a persistent pointer.

## 4. Strings and encodings

- A generated `const char*` parameter may be mapped to Kotlin `String` with implicit UTF-8 conversion. Inspect the generated binding. Disable automatic conversion per function with `noStringConversion` in `.def` when raw pointers are required.
- `String.cstr` creates UTF-8, NUL-terminated C character data. Use in `memScoped` for pointer lifetimes confined to that scope.
- Windows Unicode Win32 functions typically expect UTF-16 wide strings. Use `"text".wcstr.ptr` inside `memScoped` for APIs whose generated signature takes a compatible wide-character pointer, and inspect the actual `wchar_t`/`LPCWSTR` binding.
- `toKString()` assumes a NUL-terminated byte string and a compatible encoding. Use explicit length-aware decoding for binary buffers or strings containing NUL.
- Never use `String.length` for UTF-8 byte-count or `sizeof(wchar_t)` without verifying the target and API.

## 5. Callback lifecycle with user data

C header example:

```c
typedef void (*event_callback)(int code, void* user_data);
void source_register(event_callback callback, void* user_data);
void source_unregister(void);
void source_wait_until_idle(void);
```

Kotlin-side pattern (API signatures must be checked against generated declarations):

```kotlin
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
import kotlinx.cinterop.*
import app.nativeapi.*

class EventSink(val onEvent: (Int) -> Unit)

private fun onNativeEvent(code: Int, raw: COpaquePointer?) {
    try {
        val sink = raw?.asStableRef<EventSink>()?.get() ?: return
        sink.onEvent(code)
    } catch (failure: Throwable) {
        // Log or report according to the API contract. Never unwind through C.
    }
}

class NativeSubscription(sink: EventSink) : AutoCloseable {
    private val stable = StableRef.create(sink)
    private var closed = false

    init {
        // Use exact generated API types and callback calling convention.
        try {
            source_register(staticCFunction(::onNativeEvent), stable.asCPointer())
        } catch (failure: Throwable) {
            stable.dispose()
            throw failure
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        source_unregister()
        source_wait_until_idle() // Only if the native library guarantees this semantics.
        stable.dispose()
    }
}
```

This is a **conceptual pattern**, not a thread-safe subscription manager: real libraries may require synchronization, may not expose `source_wait_until_idle`, or may invoke callbacks concurrently. Before implementing, establish exact unregister-completion guarantees. If no safe quiescence mechanism exists, do not dispose a `StableRef` while callbacks remain possible. Avoid holding locks across calls that can synchronously invoke callbacks. For Windows callbacks, additionally confirm the ABI/calling convention.

## 6. Error paths

- C convention: check return codes and NULL; capture `errno` immediately when defined as meaningful; include function name and relevant path/parameters in diagnostic messages.
- Windows: where the API specifies `GetLastError()`, query immediately after the failing call; `HRESULT` requires its own success/failure handling, not `GetLastError` by default.
- macOS Core Foundation: handle `Create`/`Copy` ownership and `CFRelease` as documented; Objective-C has separate reference/lifecycle semantics.
- Never let Kotlin exceptions cross a `staticCFunction` callback; translate them into an API-specific code or state.
- Do not assume C calls are cancel-safe; use the native API's cancellation and close semantics.

## 7. Call directions

**Kotlin calling C**: use bundled `platform.*` or `.def` generated declarations.

**C calling Kotlin**: choose either a callback (`staticCFunction` and optional `StableRef` user data) or produce a Kotlin/Native dynamic/static library with a generated C header. Use the official Kotlin/Native dynamic-library tutorial; keep exported APIs minimal. Do not confuse callback signatures with exported Kotlin runtime APIs.
