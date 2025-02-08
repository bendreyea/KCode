package org.editor.application

/**
 * Represents the position of a row and column.
 *
 * @property row The number of the row (zero-based).
 * @property col The symbol position on the row.
 * @throws IllegalArgumentException if row or col is negative.
 */
data class UserCaret(val row: Int, val col: Int) : Comparable<UserCaret> {

    init {
        require(row >= 0 && col >= 0) { "Invalid position: ($row, $col). Row and column must be non-negative." }
    }

    companion object {
        /**
         * Creates a new [UserCaret] instance.
         *
         * @param row The number of the row (zero-based).
         * @param col The byte position on the row.
         * @return A new [UserCaret] instance.
         */
        fun create(row: Int, col: Int): UserCaret = UserCaret(row, col)
    }

    /**
     * Creates a new [UserCaret] instance with the specified row.
     *
     * @param row The new row number (zero-based).
     * @return A new [UserCaret] instance with the updated row.
     */
    fun withRow(row: Int): UserCaret = copy(row = row)

    /**
     * Creates a new [UserCaret] instance with the specified column.
     *
     * @param col The new byte position on the row.
     * @return A new [UserCaret] instance with the updated column.
     */
    fun withCol(col: Int): UserCaret = copy(col = col)

    /**
     * Compares this position with another [UserCaret] instance.
     *
     * @param other The other [UserCaret] to compare against.
     * @return A negative integer, zero, or a positive integer as this [UserCaret] is less than,
     * equal to, or greater than the specified [UserCaret].
     */
    override fun compareTo(other: UserCaret): Int {
        return when {
            this.row != other.row -> this.row - other.row
            else -> this.col - other.col
        }
    }
}
