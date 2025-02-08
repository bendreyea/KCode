package org.editor.application

import org.editor.core.PieceTable
import org.editor.core.Rope
import org.editor.core.TextBuffer
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset

/**
 * Implementation of the [Document] interface.
 */
class DocumentImpl private constructor(
    private val pt: TextBuffer,
    private val charset: Charset,
    private val bom: ByteArray,
    private val newLine: NewLine,
    private val index: RowIndex,
) : Document {

    companion object {
        /**
         * Creates a new [DocumentImpl] instance with configurable charset, BOM, and newline.
         *
         * @param charset The [Charset] used for encoding/decoding text (default: UTF-8).
         * @param bom The Byte Order Mark to use (default: empty).
         * @param newLine The newline ending to use (default: platform default).
         */
        fun create(
            charset: Charset = Charset.forName("UTF-8"),
            bom: ByteArray = ByteArray(0),
            newLine: NewLine = NewLine.platform
        ): DocumentImpl {
            val pt = PieceTable.create()
            // Create a RowIndex that uses the newline ending’s bytes.
            val rowIndex = RowIndex.create(newLine.str().toByteArray())
            return DocumentImpl(pt, charset, bom, newLine, rowIndex)
        }

        fun createWithRope(
            charset: Charset = Charset.forName("UTF-8"),
            bom: ByteArray = ByteArray(0),
            newLine: NewLine = NewLine.platform
        ): DocumentImpl {
            val pt = Rope.create()
            val rowIndex = RowIndex.create(newLine.str().toByteArray())
            return DocumentImpl(pt, charset, bom, newLine, rowIndex)
        }
    }

    override fun insert(row: Int, col: Int, cs: CharSequence) {
        // Convert the user’s char offset to a byte offset
        val byteCol = charOffsetToByteOffset(row, col)
        val insertBytes = cs.toString().toByteArray(charset)
        insert(row, byteCol, insertBytes)
    }

    override fun delete(row: Int, col: Int, cs: CharSequence) {
        val deleteBytes = cs.toString().toByteArray(charset)
        if (deleteBytes.isEmpty()) return

        // Special case: deleting exactly the document's newline sequence
        // at the start of row > 0.
        if (deleteBytes.contentEquals(newLine.bytes()) && row > 0 && col == 0) {
            val rowLens = index.rowLengths() // snapshot of row lengths (in bytes)
            // Ensure the previous row contains at least the newline bytes.
            val prevRowLen = rowLens[row - 1]
            require(prevRowLen >= newLine.bytes().size) {
                "Previous row length must be at least ${newLine.bytes().size} if newline is present; found $prevRowLen"
            }
            // The deletion should remove the newline sequence at the end of the previous row.
            val bytePos = index.serial(row - 1, prevRowLen - newLine.bytes().size)
            pt.delete(bytePos, newLine.bytes().size)
            index.delete(row - 1, prevRowLen - newLine.bytes().size, newLine.bytes().size)
        } else {
            val byteCol = charOffsetToByteOffset(row, col)
            val bytePos = index.serial(row, byteCol)
            pt.delete(bytePos, deleteBytes.size)
            index.delete(row, byteCol, deleteBytes.size)
        }
    }


    override fun getText(row: Int): CharSequence {
        return String(get(row), charset)
    }

    private fun insert(row: Int, rawCol: Int, bytes: ByteArray) {
        val adjustedCol = if (row == 0) rawCol + bom.size else rawCol
        pt.insert(index.serial(row, adjustedCol), bytes)
        index.insert(row, adjustedCol, bytes)
    }

    override fun get(row: Int): ByteArray {
        val col = index.get(row) + if (row == 0) bom.size else 0
        val len = if (row + 1 < index.rowSize()) {
            (index.get(row + 1) - col).toInt()
        } else {
            (pt.length() - col).toInt()
        }
        return pt.get(col, len)!!
    }

    override fun rows(): Int = index.rowSize()

    override fun rawSize(): Long = pt.length() - bom.size

    override fun serial(row: Int, col: Int): Long = index.serial(row, col)

    override fun pos(serial: Long): UserCaret {
        val (row, byteCol) = index.pos(serial)
        return UserCaret(row, byteOffsetToCharOffset(row, byteCol))
    }

    override fun charset(): Charset = charset

    override fun rowEnding(): NewLine = newLine

    override fun bom(): ByteArray = bom.copyOf()

    /**
     * Converts a character offset (as seen by the user) into the corresponding byte offset
     * for the given row.
     */
    override fun charOffsetToByteOffset(row: Int, charOffset: Int): Int {
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
    override fun byteOffsetToCharOffset(row: Int, byteOffset: Int): Int {
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
}
