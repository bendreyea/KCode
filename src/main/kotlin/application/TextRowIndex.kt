package org.editor.application

import org.editor.application.common.LineSeparator
import org.editor.application.common.OSTRowIndex

/**
 * RowInd for string–based text. Uses the platform’s newline string by default.
 */
class TextRowIndex private constructor(
    cacheInterval: Int,
    newlineStr: String
) : OSTRowIndex<String, TextRowIndex>(
    cacheInterval,
    newlineStr,
    splitFunction = { data ->
        if (data.isEmpty()) {
            IntArray(0)
        } else {
            val result = mutableListOf<Int>()
            var count = 0
            var i = 0
            val nlLen = newlineStr.length
            while (i < data.length) {
                if (i + nlLen <= data.length && data.substring(i, i + nlLen) == newlineStr) {
                    count += nlLen
                    result.add(count)
                    count = 0
                    i += nlLen
                } else {
                    count++
                    i++
                }
            }
            result.add(count)
            result.toIntArray()
        }
    }
) {

    companion object {
        /**
         * Creates a RowInd with the platform newline and cache interval 100.
         */
        fun create(): TextRowIndex = create(LineSeparator.platform.str(), 100)

        /**
         * Creates a RowInd with the platform newline and the specified cache interval.
         */
        fun create(cacheInterval: Int): TextRowIndex = create(LineSeparator.platform.str(), cacheInterval)

        /**
         * Creates a RowInd with the provided newline string and optional cache interval.
         */
        fun create(newlineStr: String, cacheInterval: Int = 100): TextRowIndex =
            TextRowIndex(cacheInterval, newlineStr)
    }

    override fun createInstance(
        cacheInterval: Int,
        newlineToken: String,
        splitFunction: (String) -> IntArray
    ): TextRowIndex {
        return TextRowIndex(cacheInterval, newlineToken)
    }
}
