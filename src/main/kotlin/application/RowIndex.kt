package org.editor.application

import org.editor.application.common.LineSeparator
import org.editor.application.common.OSTRowIndex

/**
 * RowIndex for byte–based text. Uses the platform’s newline bytes by default.
 */
class RowIndex private constructor(
    newlineBytes: ByteArray
) : OSTRowIndex<ByteArray, RowIndex>(
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
         * Creates a RowIndex with the platform newline.
         */
        fun create(): RowIndex = create(LineSeparator.platform.bytes())

        /**
         * Creates a RowIndex with the provided newline bytes and optional cache interval.
         */
        fun create(newlineBytes: ByteArray): RowIndex =
            RowIndex(newlineBytes)
    }

    override fun createInstance(
        newlineToken: ByteArray,
        splitFunction: (ByteArray) -> IntArray
    ): RowIndex {
        return RowIndex(newlineToken)
    }
}
