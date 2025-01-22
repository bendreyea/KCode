package org.editor.syntax.intervalTree

/**
 * Closed-open, [), interval on the integer number line.
 */
interface Interval : Comparable<Interval> {

    /**
     * The starting point of this interval.
     */
    val start: Int

    /**
     * The ending point of this interval.
     *
     * The interval does not include this point.
     */
    val end: Int

    /**
     * The length of this interval.
     */
    val length: Int
        get() = end - start

    /**
     * Checks if this interval is adjacent to the specified interval.
     *
     * Two intervals are adjacent if either one ends where the other starts.
     * @param other The interval to compare this one to.
     * @return True if this interval is adjacent to the specified interval.
     */
    fun isAdjacent(other: Interval): Boolean {
        return start == other.end || end == other.start
    }

    /**
     * Checks if this interval overlaps with the specified interval.
     *
     * @param other The interval to compare this one to.
     * @return True if this interval overlaps with the specified interval.
     */
    fun overlaps(other: Interval): Boolean {
        return end > other.start && other.end > start
    }

    /**
     * Compares this interval to another based on start and end points.
     */
    override fun compareTo(other: Interval): Int {
        return when {
            start > other.start -> 1
            start < other.start -> -1
            end > other.end -> 1
            end < other.end -> -1
            else -> 0
        }
    }
}