package org.editor.application

import java.nio.charset.Charset

/**
 * An interface that your main class implements to define text editing operations.
 */
interface TextEdit {
    /**
     * Inserts [text] at the specified position ([row], [col]).
     *
     * @param row The row index where the text will be inserted.
     * @param col The column index within the row where the text will be inserted.
     * @param text The text to insert.
     * @return The new cursor position after insertion.
     */
    fun insert(row: Int, col: Int, text: String): UserCaret

    /**
     * Deletes [len] characters starting at the specified position ([row], [col]),
     * moving to the right.
     *
     * @param row The row index from which to start deletion.
     * @param col The column index within the row from which to start deletion.
     * @param len The number of characters to delete.
     * @return The cursor position after deletion.
     */
    fun delete(row: Int, col: Int, len: Int = 1): UserCaret

    /**
     * Backspaces [len] characters starting at the specified position ([row], [col]),
     * moving to the left.
     *
     * @param row The row index from which to start backspacing.
     * @param col The column index within the row from which to start backspacing.
     * @param len The number of characters to backspace.
     * @return The new cursor position after backspacing.
     */
    fun backspace(row: Int, col: Int, len: Int = 1): UserCaret

    /**
     * Replaces [len] characters at the specified position ([row], [col]) with [text].
     *
     * @param row The row index where the replacement starts.
     * @param col The column index within the row where the replacement starts.
     * @param len The number of characters to replace.
     * @param text The text to insert in place of the replaced characters.
     * @return The cursor position after replacement.
     */
    fun replace(row: Int, col: Int, len: Int, text: String): UserCaret

    /**
     * Retrieves the text at the specified [row].
     *
     * @param row The row index from which to retrieve text.
     * @return The text at the specified row.
     */
    fun getText(row: Int): String

    /**
     * Retrieves the text between cursor positions [start] and [end].
     *
     * @param start The starting [UserCaret] position.
     * @param end The ending [UserCaret] position.
     * @return The concatenated text between [start] and [end].
     */
    fun getText(start: UserCaret, end: UserCaret): String

    /**
     * Retrieves the total number of rows in the document.
     *
     * @return The number of rows.
     */
    fun rows(): Int

    /**
     * Retrieves the character set used by the document.
     *
     * @return The character set.
     */
    fun charset(): Charset
}