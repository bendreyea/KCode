package org.editor.application

/**
 * Represents the position of a row and column.
 *
 * @property row The number of the row (zero-based).
 * @property col The byte position on the row.
 * @throws IllegalArgumentException if row or col is negative.
 */
data class Caret(val row: Int, val col: Int) : Comparable<Caret> {

    init {
        require(row >= 0 && col >= 0) { "Invalid position: ($row, $col). Row and column must be non-negative." }
    }

    companion object {
        /**
         * Creates a new [Caret] instance.
         *
         * @param row The number of the row (zero-based).
         * @param col The byte position on the row.
         * @return A new [Caret] instance.
         */
        fun create(row: Int, col: Int): Caret = Caret(row, col)

        fun toUserCaret(caret: Caret, document: Document): UserCaret {
            val charCol = document.byteOffsetToCharOffset(caret.row, caret.col)
            return UserCaret(caret.row, charCol)
        }

        fun toInternalCaret(userCaret: UserCaret, document: Document): Caret {
            val byteCol = document.charOffsetToByteOffset(userCaret.row, userCaret.col)
            return Caret(userCaret.row, byteCol)
        }
    }

    /**
     * Creates a new [Caret] instance with the specified row.
     *
     * @param row The new row number (zero-based).
     * @return A new [Caret] instance with the updated row.
     */
    fun withRow(row: Int): Caret = copy(row = row)

    /**
     * Creates a new [Caret] instance with the specified column.
     *
     * @param col The new byte position on the row.
     * @return A new [Caret] instance with the updated column.
     */
    fun withCol(col: Int): Caret = copy(col = col)

    /**
     * Compares this position with another [Caret] instance.
     *
     * @param other The other [Caret] to compare against.
     * @return A negative integer, zero, or a positive integer as this [Caret] is less than,
     * equal to, or greater than the specified [Caret].
     */
    override fun compareTo(other: Caret): Int {
        return when {
            this.row != other.row -> this.row - other.row
            else -> this.col - other.col
        }
    }
}
