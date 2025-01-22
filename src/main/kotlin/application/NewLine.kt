package org.editor.application

/**
 * Represents different types of newline (line ending) characters.
 */
enum class NewLine {

    /**
     * Line Feed (`\n`).
     */
    LF,

    /**
     * Carriage Return (`\r`).
     */
    CR,

    /**
     * Carriage Return followed by Line Feed (`\r\n`).
     */
    CRLF;

    /**
     * Unifies the line endings in the given text based on the current `NewLine` type.
     *
     * @param text The input text to unify line endings.
     * @return The text with unified line endings.
     */
    fun unify(text: CharSequence): CharSequence {
        return when (this) {
            LF -> unifyLf(text)
            CR -> unifyCr(text)
            CRLF -> unifyCrLf(text)
        }
    }

    /**
     * Returns the string representation of the newline character(s).
     *
     * @return The newline string.
     */
    fun str(): String {
        return when (this) {
            LF -> "\n"
            CR -> "\r"
            CRLF -> "\r\n"
        }
    }

    companion object {
        /**
         * The platform-specific newline character(s).
         */
        val platform: NewLine by lazy { determinePlatformNewLine() }

        /**
         * Estimates the most likely `NewLine` type based on counts of carriage returns and line feeds.
         *
         * @param crCount The number of carriage return characters (`\r`).
         * @param lfCount The number of line feed characters (`\n`).
         * @return The estimated `NewLine` type.
         */
        fun estimate(crCount: Int, lfCount: Int): NewLine {
            return when {
                crCount == 0 && lfCount == 0 -> platform
                crCount == lfCount -> CRLF
                lfCount == 0 -> CR
                else -> LF
            }
        }

        /**
         * Determines the platform-specific newline character(s) using `System.lineSeparator()`.
         *
         * @return The corresponding `NewLine` enum value.
         */
        private fun determinePlatformNewLine(): NewLine {
            return when (System.lineSeparator()) {
                "\r" -> CR
                "\r\n" -> CRLF
                else -> LF
            }
        }
    }

    /**
     * Converts all line endings in the text to Line Feed (`\n`).
     *
     * @param text The input text.
     * @return The text with unified Line Feed endings.
     */
    private fun unifyLf(text: CharSequence): CharSequence {
        var previousIndex = 0
        val stringBuilder = StringBuilder()
        var index = 0

        while (index < text.length) {
            when (text[index]) {
                '\r' -> {
                    stringBuilder.append(text, previousIndex, index).append('\n')
                    if (index + 1 < text.length && text[index + 1] == '\n') {
                        index++ // Skip the next '\n' as it's part of '\r\n'
                    }
                    previousIndex = index + 1
                }
            }
            index++
        }

        stringBuilder.append(text, previousIndex, text.length)
        return stringBuilder.toString()
    }

    /**
     * Converts all line endings in the text to Carriage Return (`\r`).
     *
     * @param text The input text.
     * @return The text with unified Carriage Return endings.
     */
    private fun unifyCr(text: CharSequence): CharSequence {
        var previousIndex = 0
        val stringBuilder = StringBuilder()
        var index = 0

        while (index < text.length) {
            when (text[index]) {
                '\n' -> {
                    stringBuilder.append(text, previousIndex, index).append('\r')
                    previousIndex = index + 1
                }
                '\r' -> {
                    if (index + 1 < text.length && text[index + 1] == '\n') {
                        stringBuilder.append(text, previousIndex, index).append('\r')
                        index++ // Skip the '\n' in '\r\n'
                        previousIndex = index + 1
                    }
                }
            }
            index++
        }

        stringBuilder.append(text, previousIndex, text.length)
        return stringBuilder.toString()
    }

    /**
     * Converts all line endings in the text to Carriage Return followed by Line Feed (`\r\n`).
     *
     * @param text The input text.
     * @return The text with unified CRLF endings.
     */
    private fun unifyCrLf(text: CharSequence): CharSequence {
        var previousIndex = 0
        val stringBuilder = StringBuilder()
        var index = 0

        while (index < text.length) {
            when (text[index]) {
                '\n' -> {
                    stringBuilder.append(text, previousIndex, index).append("\r\n")
                    previousIndex = index + 1
                }
                '\r' -> {
                    if (index + 1 < text.length && text[index + 1] == '\n') {
                        index++ // Skip the '\n' in '\r\n'
                    } else {
                        stringBuilder.append(text, previousIndex, index).append("\r\n")
                        previousIndex = index + 1
                    }
                }
            }
            index++
        }

        stringBuilder.append(text, previousIndex, text.length)
        return stringBuilder.toString()
    }
}
