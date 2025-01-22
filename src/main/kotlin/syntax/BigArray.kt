package org.editor.syntax

class BigArray<T> (
    private var arrays: Array<Array<T>>,
    var length: Int,
    var numberOfArrays: Int,
    private val arrayFactory: () -> Array<T>,
    var tag: String = ""
) {

    companion object {
        const val SHIFT_COUNT = 10
        const val GRANULARITY = 1 shl SHIFT_COUNT // 1024
        const val GRANULARITY_MASK = GRANULARITY - 1

        /**
         * Non-reified creation method using a defaultValue.
         * We do not inline or use reified here.
         */
        fun <T> create(initialSize: Int, defaultValue: T): BigArray<T> {
            require(initialSize >= 0) { "Initial size must be non-negative." }
            val numberOfArrays = if (initialSize % GRANULARITY == 0) {
                initialSize / GRANULARITY
            } else {
                initialSize / GRANULARITY + 1
            }

            val arrayFactory = { fillPartition(defaultValue) }
            val arrays = buildPartitions(numberOfArrays, arrayFactory)
            val length = numberOfArrays * GRANULARITY
            return BigArray(arrays, length, numberOfArrays, arrayFactory)
        }

        /**
         * Non-reified creation method using an initializer function.
         * The initializer function takes the index within a single partition.
         */
        fun <T> create(initialSize: Int, initializer: (Int) -> T): BigArray<T> {
            require(initialSize >= 0) { "Initial size must be non-negative." }
            val numberOfArrays = if (initialSize % GRANULARITY == 0) {
                initialSize / GRANULARITY
            } else {
                initialSize / GRANULARITY + 1
            }

            // We'll build each partition with a "local" index initializer
            val arrayFactory = {
                @Suppress("UNCHECKED_CAST")
                val arr = arrayOfNulls<Any?>(GRANULARITY) as Array<T>
                for (i in arr.indices) {
                    arr[i] = initializer(i)
                }
                arr
            }

            val arrays = buildPartitions(numberOfArrays, arrayFactory)
            val length = numberOfArrays * GRANULARITY
            return BigArray(arrays, length, numberOfArrays, arrayFactory)
        }

        /**
         * Helper: builds an Array of partitions, each created by [factory],
         * without relying on reified calls.
         */
        private fun <T> buildPartitions(
            count: Int,
            factory: () -> Array<T>
        ): Array<Array<T>> {
            @Suppress("UNCHECKED_CAST")
            val result = arrayOfNulls<Any?>(count) as Array<Array<T>>
            for (i in 0 until count) {
                result[i] = factory()
            }
            return result
        }

        /**
         * Helper: fills a new partition with [defaultValue].
         */
        private fun <T> fillPartition(defaultValue: T): Array<T> {
            @Suppress("UNCHECKED_CAST")
            val arr = arrayOfNulls<Any?>(GRANULARITY) as Array<T>
            for (i in arr.indices) {
                arr[i] = defaultValue
            }
            return arr
        }
    }

    operator fun get(index: Int): T {
        if (index < 0 || index >= length) {
            throw IndexOutOfBoundsException(
                "Getter: Index '$index' out of bounds. Must be in [0, ${length - 1}]"
            )
        }
        val arrayIndex = index shr SHIFT_COUNT
        val innerIndex = index and GRANULARITY_MASK
        return arrays[arrayIndex][innerIndex]
    }

    operator fun set(index: Int, value: T) {
        if (index >= length) {
            resizeForIndex(index)
        }
        val arrayIndex = index shr SHIFT_COUNT
        val innerIndex = index and GRANULARITY_MASK
        arrays[arrayIndex][innerIndex] = value
    }

    private fun resizeForIndex(index: Int) {
        val requiredArrays = (index shr SHIFT_COUNT) + 1
        if (requiredArrays > numberOfArrays) {
            val newSize = maxOf(requiredArrays, numberOfArrays * 2)

            @Suppress("UNCHECKED_CAST")
            val newArrays = arrays.copyOf(newSize) as Array<Array<T>>
            // Initialize new partitions
            for (i in numberOfArrays until newSize) {
                newArrays[i] = arrayFactory()
            }
            arrays = newArrays
            numberOfArrays = newSize
            length = numberOfArrays * GRANULARITY
        }
    }

    fun getPartitionNumber(index: Int): Int {
        if (index < 0 || index >= length) {
            throw IndexOutOfBoundsException(
                "getPartitionNumber: Index '$index' out of bounds."
            )
        }
        return index shr SHIFT_COUNT
    }

    fun getPartition(partitionNumber: Int): Array<T> {
        if (partitionNumber < 0 || partitionNumber >= numberOfArrays) {
            throw IndexOutOfBoundsException(
                "getPartition: Partition '$partitionNumber' out of range."
            )
        }
        return arrays[partitionNumber]
    }

    fun slice(partitionNumber: Int, modify: (Array<T>) -> Unit) {
        if (partitionNumber < 0 || partitionNumber >= numberOfArrays) {
            throw IndexOutOfBoundsException(
                "slice: Partition '$partitionNumber' out of range."
            )
        }
        val partitionRef = arrays[partitionNumber]
        modify(partitionRef)
        arrays[partitionNumber] = partitionRef
    }

    /**
     * Clears all elements in the BigArray by reinitializing each partition
     */
    fun clear(defaultValue: T? = null) {
        // If defaultValue != null, fill partitions with that;
        // or just re-create using arrayFactory for each partition.
        for (i in arrays.indices) {
            if (defaultValue != null) {
                for (j in arrays[i].indices) {
                    arrays[i][j] = defaultValue
                }
            } else {
                arrays[i] = arrayFactory()
            }
        }
        length = 0
        numberOfArrays = 0
    }

    /**
     * Copies this BigArray into a new one with size [newSize].
     * If [newSize] is larger, new partitions are initialized via [arrayFactory].
     */
    fun copy(newSize: Int): BigArray<T> {
        require(newSize >= 0) { "New size must be non-negative." }

        val numberOfArraysNew = if (newSize % GRANULARITY == 0) {
            newSize / GRANULARITY
        } else {
            newSize / GRANULARITY + 1
        }

        // Create new partitions:
        val newArrays = buildPartitions(numberOfArraysNew, arrayFactory)

        // Copy data from old partitions
        val copyLimit = minOf(numberOfArrays, numberOfArraysNew)
        for (a in 0 until copyLimit) {
            arrays[a].copyInto(newArrays[a], 0, 0, GRANULARITY)
        }

        val newLength = numberOfArraysNew * GRANULARITY
        return BigArray(newArrays, newLength, numberOfArraysNew, arrayFactory, tag)
    }
}
