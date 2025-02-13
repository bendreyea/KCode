package org.editor.application

import org.editor.application.common.LineSeparator
import org.editor.application.common.OSTRowIndex

/**
 * TextRowIndex for string–based text. Uses the platform’s newline string by default.
 */
class TextRowIndex private constructor(
    newlineStr: String
) : OSTRowIndex<String, TextRowIndex>(
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
         * Creates a RowInd with the platform newline
         */
        fun create(): TextRowIndex = create(LineSeparator.platform.str())


        /**
         * Creates a RowInd with the provided newline string and optional cache interval.
         */
        fun create(newlineStr: String): TextRowIndex =
            TextRowIndex(newlineStr)
    }

    override fun createInstance(
        newlineToken: String,
        splitFunction: (String) -> IntArray
    ): TextRowIndex {
        return TextRowIndex(newlineToken)
    }
}
