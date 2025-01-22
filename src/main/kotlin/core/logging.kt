package org.editor.core

inline fun <T> logPerformance(label: String, block: () -> T): T {
    val startTime = System.nanoTime()
    val result = block()
    val endTime = System.nanoTime()
    println("$label took ${(endTime - startTime) / 1_000_000} ms")
    return result
}
