package org.editor.application

/**
 * Manages the mapping between byte positions and row/column positions.
 *
 */
class RowIndex private constructor(
    private val cacheInterval: Int,
    private val newlineBytes: ByteArray
) {

    private var rowLengths = intArrayOf(0)
    private var length = 1
    private var subTotalCache = longArrayOf(0)
    private var cacheLength = 1

    companion object {
        /**
         * Creates a new RowIndex using the default newline (platform newline) and cache interval 100.
         */
        fun create(): RowIndex = create(NewLine.platform.bytes(), 100)

        /**
         * Creates a new RowIndex using the default newline (platform newline) and the given cache interval.
         */
        fun create(cacheInterval: Int): RowIndex = create(NewLine.platform.bytes(), cacheInterval)

        /**
         * Creates a new RowIndex using the provided newline bytes and optional cache interval.
         */
        fun create(newlineBytes: ByteArray, cacheInterval: Int = 100): RowIndex =
            RowIndex(cacheInterval, newlineBytes)

        /**
         * Splits the given byte array into an array of row lengths based on the provided newline sequence.
         * Each returned row length includes any newline bytes that terminated the row.
         */
        fun rows(bytes: ByteArray, newlineBytes: ByteArray): IntArray {
            if (bytes.isEmpty())
                return IntArray(0)
            val rowLengths = mutableListOf<Int>()
            var count = 0
            var index = 0
            while (index < bytes.size) {
                if (index + newlineBytes.size <= bytes.size &&
                    newlineBytes.indices.all { bytes[index + it] == newlineBytes[it] }
                ) {
                    // Include the newline bytes in the current row's count.
                    count += newlineBytes.size
                    rowLengths.add(count)
                    count = 0
                    index += newlineBytes.size
                } else {
                    count++
                    index++
                }
            }
            rowLengths.add(count)
            return rowLengths.toIntArray()
        }

        /**
         * Overload that uses the default newline (platform newline).
         */
        fun rows(bytes: ByteArray): IntArray = rows(bytes, NewLine.platform.bytes())
    }

    /** Adds the specified byte array to the end of the index. */
    fun add(bytes: ByteArray) {
        val rows = rows(bytes)
        if (rows.isEmpty())
            return

        if (length + rows.size > rowLengths.size) {
            rowLengths = growRowLengths(length + rows.size)
        }

        if (length == 0) {
            length++
        }

        for (i in rows.indices) {
            // Add to the last row
            rowLengths[length - 1] += rows[i]
            if (rows.size > 1 && i < rows.size - 1) {
                length++
            }
        }
    }

    /**
     * Gets the total byte length from row 0 up to (but not including) [row].
     */
    fun get(row: Int): Long {
        require(row in 0 until length) { "Row index out of bounds: $row" }
        var startRow = 0
        var startPos = 0L
        val cacheIndex = row / cacheInterval
        if (cacheIndex in 1 until cacheLength) {
            startRow = cacheIndex * cacheInterval
            startPos = subTotalCache[cacheIndex]
        }
        for (i in startRow until minOf(length, row)) {
            if (i % cacheInterval == 0) {
                if (cacheLength + 1 > subTotalCache.size) {
                    subTotalCache = growCache(cacheLength + 1)
                }
                val chIndex = i / cacheInterval
                subTotalCache[chIndex] = startPos
                cacheLength = chIndex + 1
            }

            startPos += rowLengths[i]
        }

        return startPos
    }

    /**
     * Inserts [bytes] at the given [row],[col].
     */
    fun insert(row: Int, col: Int, bytes: ByteArray) {
        require(row in 0 until length) { "Row index out of bounds: $row" }
        require(col in 0..rowLengths[row]) { "Column index out of bounds: $col" }

        val rows = rows(bytes)
        if (rows.isEmpty()) return

        if (length + rows.size > rowLengths.size) {
            rowLengths = growRowLengths(length + rows.size)
        }

        cacheLength = row / cacheInterval

        if (rows.size == 1) {
            // Insert within a single row
            rowLengths[row] += rows[0]
        } else {
            // Insert across multiple rows
            val head = col + rows[0]
            val tail = (rowLengths[row] - col) + rows[rows.size - 1]
            System.arraycopy(
                rowLengths, row + 1,
                rowLengths, row + rows.size,
                length - (row + 1)
            )
            rowLengths[row] = head
            System.arraycopy(rows, 1, rowLengths, row + 1, rows.size - 2)
            rowLengths[row + rows.size - 1] = tail
            length += (rows.size - 1)
        }
    }

    /**
     * Deletes [len] bytes starting at [row],[col].
     */
    fun delete(row: Int, col: Int, len: Int) {
        require(row in 0 until length) { "Row index out of bounds: $row" }
        require(col in 0..rowLengths[row]) { "Column index out of bounds: $col" }
        require(len >= 0) { "Length to delete must be non-negative" }
        if (len == 0) return

        cacheLength = row / cacheInterval

        if ((rowLengths[row] - col) > len) {
            rowLengths[row] -= len
        } else {
            var remain = len - (rowLengths[row] - col)
            rowLengths[row] = col
            var lines = 0
            do {
                if ((row + lines + 1) >= length) break
                remain -= rowLengths[row + ++lines]
            } while (remain >= 0)
            rowLengths[row] += (-remain)
            if (lines > 0) {
                System.arraycopy(
                    rowLengths, row + 1 + lines,
                    rowLengths, row + 1,
                    length - (row + 1 + lines)
                )
                length -= lines
            }
        }
    }

    /** Returns the total number of rows. */
    fun rowSize(): Int = length

    /**
     * Returns the absolute “serial” offset corresponding to [row],[col].
     */
    fun serial(row: Int, col: Int): Long {
        require(row in 0 until length)
        require(col in 0..rowLengths[row])
        return get(row) + col
    }

    /**
     * Returns the row and column for the given serial offset.
     */
    fun pos(serial: Long): Pair<Int, Int> {
        require(serial >= 0) { "Serial position must be non-negative: $serial" }
        val result = subTotalCache.binarySearch(serial, 0, cacheLength)
        if (result >= 0) {
            return Pair(result * cacheInterval, 0)
        } else {
            val point = maxOf(result.inv() - 1, 0)
            var st = subTotalCache[point]
            for (i in (point * cacheInterval) until length) {
                val ln = rowLengths[i]
                if (st + ln > serial) {
                    return Pair(i, (serial - st).toInt())
                }
                st += ln
            }
            return Pair(length - 1, rowLengths[length - 1])
        }
    }

    /** Returns a copy of [rowLengths] up to the current length. */
    fun rowLengths(): IntArray = rowLengths.copyOf(length)

    /** Returns a copy of [subTotalCache] up to the current cache length. */
    fun stCache(): LongArray = subTotalCache.copyOf(cacheLength)

    private fun growRowLengths(minCapacity: Int): IntArray {
        val oldCap = rowLengths.size
        val newCap = if (oldCap > 0)
            minOf(maxOf(minCapacity, oldCap shl 1), Int.MAX_VALUE - 8)
        else
            maxOf(100, minCapacity)
        return rowLengths.copyOf(newCap).also { rowLengths = it }
    }

    private fun growCache(minCapacity: Int): LongArray {
        val oldCap = subTotalCache.size
        val newCap = if (oldCap > 0)
            minOf(maxOf(minCapacity, oldCap shl 1), Int.MAX_VALUE - 8)
        else
            maxOf(10, minCapacity)
        return subTotalCache.copyOf(newCap).also { subTotalCache = it }
    }
}
