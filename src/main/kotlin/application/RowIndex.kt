package org.editor.application

import org.editor.application.common.LineSeparator
import org.editor.application.common.OSTRowIndex

/**
 * RowIndex for byte–based text. Uses the platform’s newline bytes by default.
 */
class RowIndex private constructor(
    cacheInterval: Int,
    newlineBytes: ByteArray
) : OSTRowIndex<ByteArray, RowIndex>(
    cacheInterval,
    newlineBytes,
    splitFunction = { data ->
        if (data.isEmpty()) {
            IntArray(0)
        } else {
            val result = mutableListOf<Int>()
            var count = 0
            var index = 0
            while (index < data.size) {
                if (index + newlineBytes.size <= data.size &&
                    newlineBytes.indices.all { data[index + it] == newlineBytes[it] }
                ) {
                    count += newlineBytes.size
                    result.add(count)
                    count = 0
                    index += newlineBytes.size
                } else {
                    count++
                    index++
                }
            }
            result.add(count)
            result.toIntArray()
        }
    }
) {

    companion object {
        /**
         * Creates a RowIndex with the platform newline and cache interval 100.
         */
        fun create(): RowIndex = create(LineSeparator.platform.bytes(), 100)

        /**
         * Creates a RowIndex with the platform newline and the specified cache interval.
         */
        fun create(cacheInterval: Int): RowIndex = create(LineSeparator.platform.bytes(), cacheInterval)

        /**
         * Creates a RowIndex with the provided newline bytes and optional cache interval.
         */
        fun create(newlineBytes: ByteArray, cacheInterval: Int = 100): RowIndex =
            RowIndex(cacheInterval, newlineBytes)
    }

    override fun createInstance(
        cacheInterval: Int,
        newlineToken: ByteArray,
        splitFunction: (ByteArray) -> IntArray
    ): RowIndex {
        return RowIndex(cacheInterval, newlineToken)
    }
}
