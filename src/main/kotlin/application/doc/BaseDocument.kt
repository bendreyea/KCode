package org.editor.application.doc

import org.editor.application.RowIndex
import org.editor.application.common.LineSeparator
import org.editor.application.common.UserCaret
import org.editor.core.TextBuffer
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset

abstract class BaseDocument(
    protected var pt: TextBuffer,
    protected val charset: Charset,
    protected val bom: ByteArray,
    protected val lineSeparator: LineSeparator,
    protected val index: RowIndex,
) {

    open fun getText(row: Int): CharSequence {
        return String(get(row), charset)
    }

    fun get(row: Int): ByteArray {
        val col = index.get(row) + if (row == 0) bom.size else 0
        val len = if (row + 1 < index.rowSize()) {
            (index.get(row + 1) - col).toInt()
        } else {
            (pt.length() - col).toInt()
        }
        return pt.get(col, len)!!
    }

    open fun rows(): Int = index.rowSize()

    open fun rawSize(): Long = pt.length() - bom.size

    open fun serial(row: Int, col: Int): Long = index.serial(row, col)

    open fun pos(serial: Long): UserCaret {
        val (row, byteCol) = index.pos(serial)
        return UserCaret(row, byteOffsetToCharOffset(row, byteCol))
    }

    open fun charset(): Charset = charset

    open fun rowEnding(): LineSeparator = lineSeparator

    open fun bom(): ByteArray = bom.copyOf()

    /**
     * Converts a character offset (as seen by the user) into the corresponding byte offset
     * for the given row.
     */
    protected fun charOffsetToByteOffset(row: Int, charOffset: Int): Int {
        val rowBytes = get(row)
        val bb = ByteBuffer.wrap(rowBytes)
        val decoder = charset.newDecoder()  // new decoder each time because it is stateful
        var currentCharCount = 0
        var lastBytePos = 0
        val charBuffer = CharBuffer.allocate(1)
        while (bb.hasRemaining() && currentCharCount < charOffset) {
            charBuffer.clear()
            val result = decoder.decode(bb, charBuffer, false)
            if (result.isError) {
                throw CharacterCodingException()
            }
            if (charBuffer.position() > 0) {
                currentCharCount++
                lastBytePos = bb.position()
            } else {
                break
            }
        }
        if (currentCharCount != charOffset) {
            throw IllegalArgumentException(
                "Row $row does not have enough characters (requested: $charOffset, but only $currentCharCount available)"
            )
        }
        return lastBytePos
    }

    /**
     * Converts a byte offset (relative to the start of the row’s bytes) to a character offset.
     */
    protected fun byteOffsetToCharOffset(row: Int, byteOffset: Int): Int {
        val rowBytes = get(row)
        if (byteOffset > rowBytes.size) {
            throw IllegalArgumentException("Byte offset $byteOffset out of range for row $row")
        }
        val bb = ByteBuffer.wrap(rowBytes, 0, byteOffset)
        val decoder = charset.newDecoder()
        val charBuffer = CharBuffer.allocate(byteOffset)
        decoder.decode(bb, charBuffer, true)
        charBuffer.flip()
        return charBuffer.length
    }

    fun lineSeparator(): LineSeparator = lineSeparator
}