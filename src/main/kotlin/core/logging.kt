package org.editor.core

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class PerformanceMetrics {
    private val count = AtomicLong(0)
    private val total = AtomicLong(0)
    private val min = AtomicReference<Long>(Long.MAX_VALUE)
    private val max = AtomicReference<Long>(Long.MIN_VALUE)

    fun record(value: Long) {
        // Update total and count for average
        total.addAndGet(value)
        count.incrementAndGet()

        // Update min
        min.getAndUpdate { currentMin ->
            if (value < currentMin)
                value else currentMin
        }

        // Update max
        max.getAndUpdate { currentMax ->
            if (value > currentMax)
                value else currentMax
        }
    }

    fun getMetrics(): Map<String, Any> {
        val currentCount = count.get()
        return mapOf(
            "min" to (min.get().takeIf { it != Long.MAX_VALUE } ?: 0L),
            "max" to (max.get().takeIf { it != Long.MIN_VALUE } ?: 0L),
            "average" to (if (currentCount > 0) total.get() / currentCount else 0L),
            "count" to currentCount
        )
    }
}

val globalMetrics = mutableMapOf<String, PerformanceMetrics>()

inline fun <T> logPerformance(label: String, block: () -> T): T {
    val metrics = globalMetrics.computeIfAbsent(label) { PerformanceMetrics() }
    val startTime = System.nanoTime()
    val result = block()
    val endTime = System.nanoTime()
    metrics.record((endTime - startTime) / 1_000_000)
    return result
}
