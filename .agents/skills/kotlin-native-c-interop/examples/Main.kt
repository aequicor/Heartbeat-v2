@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

import sample.nativebridge.native_add

fun main() {
    check(native_add(20, 22) == 42)
    println("C interop OK")
}
