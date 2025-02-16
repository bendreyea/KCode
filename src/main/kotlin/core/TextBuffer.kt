package org.editor.core

interface TextBuffer {
    /**
     * Inserts the bytes into this `TextBuffer`.
     * @param pos the offset
     * @param bytes the bytes to be inserted
     */
    fun insert(pos: Long, bytes: ByteArray?)

    /**
     * Inserts the bytes into this `TextBuffer`.
     * @param pos the offset
     * @param bytes the bytes to be inserted
     */
    fun withInsert(pos: Long, bytes: ByteArray): TextBuffer

    /**
     * Removes the bytes in a substring of this `TextBuffer`.
     * @param pos the beginning index, inclusive
     * @param len the length to be deleted
     */
    fun delete(pos: Long, len: Int)

    /**
     * Removes the bytes in a substring of this `TextBuffer`.
     * @param pos the beginning index, inclusive
     * @param len the length to be deleted
     */
    fun withDelete(pos: Long, len: Int): TextBuffer

    /**
     * Get the byte array of the specified range of this text buffer.
     * @param pos the start index of the range to be copied, inclusive
     * @param len the length of the range to be copied
     * @return the byte array of the specified range of this text buffer
     */
    fun get(pos: Long, len: Int): ByteArray?

    /**
     * Get the length of bytes this text buffer holds.
     * @return the length of bytes
     */
    fun length(): Long

    fun bytes(): ByteArray

    /**
     * Get a snapshot of this text buffer.
     * @return a snapshot of this text buffer
     */
    fun snapshot(): TextBuffer
}