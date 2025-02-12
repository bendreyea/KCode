package org.editor.application.doc

import org.editor.application.common.LineSeparator
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
     * Retrieves the number of rows in this [Document].
     *
     * @return The number of rows.
     */
    fun rows(): Int

    /**
     * Retrieves the charset used by this [Document].
     *
     * @return The [Charset] of this document.
     */
    fun charset(): Charset

    /**
     * Retrieves the line separator used by this [Document].
     *
     * @return The [LineSeparator] of this document.
     */
    fun lineSeparator(): LineSeparator

    /**
     * Adds a listener to this [Document] that will be notified of changes.
     */
    fun addListener(listener: DocumentChangeListener)
}
