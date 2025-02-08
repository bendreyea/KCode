package org.editor.application

import java.nio.charset.Charset

/**
 * Represents a document that supports various text and byte operations.
 */
interface Document {

    /**
     * Inserts the given character sequence into this [Document].
     *
     * @param row The number of the row (zero-based).
     * @param col The byte position on the row where the character sequence is to be inserted.
     * @param cs The character sequence to be inserted.
     */
    fun insert(row: Int, col: Int, cs: CharSequence)

    /**
     * Deletes the specified character sequence from this [Document].
     *
     * @param row The number of the row (zero-based).
     * @param col The byte position on the row where the character sequence is to be deleted.
     * @param cs The character sequence to be deleted.
     */
    fun delete(row: Int, col: Int, cs: CharSequence)

    /**
     * Retrieves the character sequence at the specified row.
     *
     * @param row The number of the row (zero-based).
     * @return The character sequence of the specified row.
     */
    fun getText(row: Int): CharSequence

    /**
     * Retrieves the byte array at the specified row.
     *
     * @param row The number of the row (zero-based).
     * @return The byte array of the specified row.
     */
    fun get(row: Int): ByteArray

    /**
     * Retrieves the number of rows in this [Document].
     *
     * @return The number of rows.
     */
    fun rows(): Int

    /**
     * Retrieves the total byte length that this [Document] holds.
     **
     * @return The total byte length of this document.
     */
    fun rawSize(): Long

    /**
     * Retrieves the serial position, representing the byte position from the beginning of the file.
     * This position does not include BOM.
     *
     * @param row The specified row.
     * @param col The specified position in the row.
     * @return The serial position.
     */
    fun serial(row: Int, col: Int): Long

    /**
     * Retrieves the row and column position based on the given serial position.
     *
     * @param serial The serial position.
     * @return The [UserCaret] representing the row and column.
     */
    fun pos(serial: Long): UserCaret

    /**
     * Retrieves the charset used by this [Document].
     *
     * @return The [Charset] of this document.
     */
    fun charset(): Charset

    /**
     * Retrieves the row ending used by this [Document].
     *
     * @return The [NewLine] of this document.
     */
    fun rowEnding(): NewLine

    /**
     * Retrieves the BOM (Byte Order Mark) of this [Document].
     *
     * @return The BOM as a byte array. If there is no BOM, returns an empty byte array.
     */
    fun bom(): ByteArray

    fun charOffsetToByteOffset(row: Int, charOffset: Int): Int
    fun byteOffsetToCharOffset(row: Int, byteOffset: Int): Int

    /**
     * Factory methods for creating instances of [Document].
     */
    companion object {
        /**
         * Creates a new [Document].
         *
         * @return A new [Document] instance.
         */
        fun create(): Document = DocumentImpl.create()

    }
}
