package org.editor.application

class RowIndex private constructor(private val cacheInterval: Int) {

    /** The row lengths. */
    private var rowLengths = intArrayOf(0)

    /** The length of [rowLengths]. */
    private var length = 1

    /** The sub-total cache. */
    private var subTotalCache = longArrayOf(0)

    /** The sub-total cache length. */
    private var cacheLength = 1

    companion object {
        /** Create a new [RowIndex] with the default cache interval of 100. */
        fun create(): RowIndex = RowIndex(100)

        /** Create a new [RowIndex] with the specified cache interval. */
        fun create(cacheInterval: Int): RowIndex = RowIndex(cacheInterval)

        /**
         * Converts the specified byte array to line-by-line byte lengths.
         *
         * For example, for bytes representing `"a\nbb\nccc"`, returns
         * `[2, 3, 4]` (depending on whether `\n` is counted, etc.).
         */
        fun rows(bytes: ByteArray): IntArray {
            if (bytes.isEmpty())
                return IntArray(0)

            val intList = mutableListOf<Int>()
            var count = 0
            for (b in bytes) {
                count++
                if (b == '\n'.code.toByte()) {
                    intList.add(count)
                    count = 0
                }
            }
            // add remaining bytes after last newline
            intList.add(count)
            return intList.toIntArray()
        }
    }

    /**
     * Adds the specified byte array to the end of the index.
     */
    fun add(bytes: ByteArray) {
        val rows = rows(bytes)
        if (rows.isEmpty()) {
            return
        }

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
     *
     * This method updates the stCache partially, exactly as in your Java code.
     */
    fun get(row: Int): Long {
        require(row in 0 until length) { "Row index out of bounds: $row" }

        var startRow = 0
        var startPos = 0L

        // If we have a cache entry for some chunk, jump there
        val cacheIndex = row / cacheInterval
        if (cacheIndex in 1..< cacheLength) {
            startRow = cacheIndex * cacheInterval
            startPos = subTotalCache[cacheIndex]
        }

        // Build partial sums up to 'row'
        for (i in startRow until minOf(length, row)) {
            // if i is a multiple of cacheInterval, store prefix-sum in stCache
            if (i % cacheInterval == 0) {
                // ensure there's room in stCache
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
     * Inserts [bytes] at the given [row], [col].
     */
    fun insert(row: Int, col: Int, bytes: ByteArray) {
        require(row in 0 until length) { "Row index out of bounds: $row" }
        require(col in 0..rowLengths[row]) { "Column index out of bounds: $col" }

        val rows = rows(bytes)
        if (rows.isEmpty()) {
            return
        }

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

            // shift existing rows downward
            System.arraycopy(
                rowLengths, row + 1,
                rowLengths, row + rows.size,
                length - (row + 1)
            )

            rowLengths[row] = head
            // copy the intermediate rows
            // the last row will be set separately
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

        // same as Java
        cacheLength = row / cacheInterval

        // if it fits fully in the row
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

            // merge leftover back
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
     * The offset is basically the sum of all rowLengths up to [row], plus [col].
     */
    fun serial(row: Int, col: Int): Long {
        require(row in 0 until length)
        require(col in 0..rowLengths[row])
        return get(row) + col
    }

    /**
     * Returns `[row, col]` for the given [serial] offset,
     * using a binary search in [subTotalCache].
     */
    fun pos(serial: Long): IntArray {
        require(serial >= 0) { "Serial position must be non-negative: $serial" }

        val result = subTotalCache.binarySearch(serial, cacheLength)
        return if (result >= 0) {
            // Found exact prefix sum
            intArrayOf(result * cacheInterval, 0)
        } else {
            val point = maxOf(result.inv() - 1, 0)
            var st = subTotalCache[point]
            for (i in (point * cacheInterval) until length) {
                val ln = rowLengths[i]
                if (st + ln > serial) {
                    return intArrayOf(i, (serial - st).toInt())
                }
                st += ln
            }
            // clamp to end
            intArrayOf(length - 1, rowLengths[length - 1])
        }
    }

    /** Returns a copy of [rowLengths] up to [length]. */
    fun rowLengths(): IntArray {
        return rowLengths.copyOf(length)
    }

    /** Returns a copy of [subTotalCache] up to [cacheLength]. */
    fun stCache(): LongArray {
        return subTotalCache.copyOf(cacheLength)
    }


    private fun growRowLengths(minCapacity: Int): IntArray {
        val oldCap = rowLengths.size
        return if (oldCap > 0) {
            val newCap = minOf(maxOf(minCapacity, oldCap shr 1), Int.MAX_VALUE - 8)
            rowLengths.copyOf(newCap)
        } else {
            IntArray(maxOf(100, minCapacity))
        }.also { rowLengths = it }
    }

    private fun growCache(minCapacity: Int): LongArray {
        val oldCap = subTotalCache.size
        return if (oldCap > 0) {
            val newCap = minOf(maxOf(minCapacity, oldCap shr 2), Int.MAX_VALUE - 8)
            subTotalCache.copyOf(newCap)
        } else {
            LongArray(maxOf(10, minCapacity))
        }.also { subTotalCache = it }
    }
}
