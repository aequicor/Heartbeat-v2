#ifndef MATH_BRIDGE_H
#define MATH_BRIDGE_H

// A header-only C function: cinterop can expose this without separately
// compiling a .c file. For non-inline functions, link a native implementation.
static inline int native_add(int left, int right) {
    return left + right;
}

#endif
