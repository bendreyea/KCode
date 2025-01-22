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
     * Inserts the specified byte array into this [Document].
     *
     * @param row The number of the row (zero-based).
     * @param rawCol The byte position on the row where the byte array is to be inserted.
     * @param bytes The byte array to be inserted. The bytes must be encoded in the appropriate [Charset].
     */
    fun insert(row: Int, rawCol: Int, bytes: ByteArray)

    /**
     * Deletes the specified byte length from this [Document].
     *
     * @param row The number of the row (zero-based).
     * @param rawCol The byte position on the row where the byte sequence is to be deleted.
     * @param rawLen The byte length to be deleted. The length must be encoded in the appropriate [Charset].
     */
    fun delete(row: Int, rawCol: Int, rawLen: Int)

    /**
     * Retrieves the byte array at the specified position.
     **
     * @param row The number of the row (zero-based).
     * @param rawCol The byte position on the row. The position must be encoded in the appropriate [Charset].
     * @param rawLen The byte length to retrieve. The length must be encoded in the appropriate [Charset].
     * @return The byte array corresponding to the specified position and length.
     */
    fun get(row: Int, rawCol: Int, rawLen: Int): ByteArray

    /**
     * Retrieves the byte array at the specified row.
     *
     * @param row The number of the row (zero-based).
     * @return The byte array of the specified row.
     */
    fun get(row: Int): ByteArray

    /**
     * Retrieves the character sequence at the specified position.
     *
     * @param row The number of the row (zero-based).
     * @param rawCol The byte position on the row. The position must be encoded in the appropriate [Charset].
     * @param rawLen The byte length to retrieve. The length must be encoded in the appropriate [Charset].
     * @return The character sequence corresponding to the specified position and length.
     */
    fun getText(row: Int, rawCol: Int, rawLen: Int): CharSequence

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
     * Retrieves the serial position based on the given [Caret].
     *
     * @param pos The [Caret] representing row and column.
     * @return The serial position.
     */
    fun serial(pos: Caret): Long = serial(pos.row, pos.col)

    /**
     * Retrieves the row and column position based on the given serial position.
     *
     * @param serial The serial position.
     * @return The [Caret] representing the row and column.
     */
    fun pos(serial: Long): Caret

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
