package org.editor.application

import org.editor.core.PieceTable
import org.editor.core.Rope
import org.editor.core.TextBuffer
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset

/**
 * Implementation of the [Document] interface.
 *
 * @property pt The [TextBuffer] used for managing text modifications.
 * @property charset The [Charset] used for encoding and decoding text.
 * @property index The [RowIndex] for managing row and column positions.
 * @property bom The Byte Order Mark (BOM) of the document.
 * @property newLine The [NewLine] used in the document.
 */
class DocumentImpl private constructor(
    private val pt: TextBuffer,
) : Document {

    /** The [Charset] of the document. */
    private val charset: Charset

    /** The [RowIndex] instance. */
    private val index: RowIndex

    /** The Byte Order Mark (BOM). */
    private val bom: ByteArray

    /** The row ending used in the document. */
    private val newLine: NewLine


    init {
        this.index = RowIndex.create()
        this.charset = Charset.forName("UTF-8")
        this.bom = ByteArray(0)
        this.newLine = NewLine.platform
    }

    companion object {
        /**
         * Creates a new [DocumentImpl] instance with default settings.
         *
         * @return A new [DocumentImpl] instance.
         */
        fun create(): DocumentImpl = DocumentImpl(PieceTable.create())

        fun createWithRope(): DocumentImpl = DocumentImpl(Rope.create());
    }

    /**
     * Inserts text at the given row and character column.
     */
    override fun insert(row: Int, col: Int, cs: CharSequence) {
        // Compute the correct byte offset from the character offset.
        val byteCol = charOffsetToByteOffset(row, col)
        // Convert the text to insert to bytes.
        val insertBytes = cs.toString().toByteArray(charset)
        insert(row, byteCol, insertBytes)
    }

    /**
     * Deletes text at the given row and character column.
     */
    override fun delete(row: Int, col: Int, cs: CharSequence) {
        val deleteBytes = cs.toString().toByteArray(charset)
        if (deleteBytes.isEmpty()) return

        // Special case: deleting exactly "\n" at the start of row > 0.
        if (deleteBytes.size == 1 &&
            deleteBytes[0] == '\n'.code.toByte() &&
            row > 0 &&
            col == 0
        ) {
            val rowLens = index.rowLengths() // snapshot of row lengths (in bytes)
            val prevRowLen = rowLens[row - 1]
            require(prevRowLen > 0) {
                "Previous row length must be > 0 if there's a newline. rowLens[row-1]=$prevRowLen"
            }
            val bytePos = index.serial(row - 1, prevRowLen - 1)
            pt.delete(bytePos, 1)
            index.delete(row - 1, prevRowLen - 1, 1)
        } else {
            // Convert the character offset to a byte offset.
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

    override fun pos(serial: Long): Caret {
        val (row, byteCol) = index.pos(serial)
        return Caret(row, byteOffsetToCharOffset(row, byteCol))
    }

    override fun charset(): Charset = charset

    override fun rowEnding(): NewLine = newLine

    override fun bom(): ByteArray = bom.copyOf()

    /**
     * Converts a character offset (as seen by the user) into the corresponding byte offset
     * for the given row. It decodes the row’s bytes (using the same charset) until it has
     * consumed exactly [charOffset] characters.
     *
     * @throws IllegalArgumentException if the row does not have enough characters.
     */
    private fun charOffsetToByteOffset(row: Int, charOffset: Int): Int {
        val rowBytes = get(row)
        val bb = ByteBuffer.wrap(rowBytes)
        // Create a new decoder each time because it is stateful.
        val decoder = charset.newDecoder()
        var currentCharCount = 0
        var lastBytePos = 0

        // We use a small (size=1) CharBuffer to decode one character at a time.
        val charBuffer = CharBuffer.allocate(1)
        while (bb.hasRemaining() && currentCharCount < charOffset) {
            // Mark the position before decoding.
            val posBefore = bb.position()
            charBuffer.clear()
            val result = decoder.decode(bb, charBuffer, false)
            if (result.isError) {
                throw CharacterCodingException()
            }
            // If a character was produced, update our count.
            if (charBuffer.position() > 0) {
                currentCharCount++
                lastBytePos = bb.position()
            } else {
                // In case no progress is made, break to avoid an infinite loop.
                break
            }
        }
        if (currentCharCount != charOffset) {
            throw IllegalArgumentException("Row $row does not have enough characters (requested: $charOffset, but only $currentCharCount available)")
        }

        return lastBytePos
    }

    /**
     * Converts a byte offset (relative to the start of the row’s bytes) to a character offset.
     * It decodes the bytes from the beginning of the row up to [byteOffset] and returns how many
     * characters were decoded.
     */
    private fun byteOffsetToCharOffset(row: Int, byteOffset: Int): Int {
        val rowBytes = get(row)
        if (byteOffset > rowBytes.size) {
            throw IllegalArgumentException("Byte offset $byteOffset out of range for row $row")
        }
        val bb = ByteBuffer.wrap(rowBytes, 0, byteOffset)
        val decoder = charset.newDecoder()
        // Allocate a buffer that is surely large enough.
        val charBuffer = CharBuffer.allocate(byteOffset)
        decoder.decode(bb, charBuffer, true)
        charBuffer.flip()
        return charBuffer.length
    }
}
