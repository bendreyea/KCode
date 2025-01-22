package org.editor.application

import org.editor.core.PieceTable
import org.editor.core.TextBuffer
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
    }

    override fun insert(row: Int, col: Int, cs: CharSequence) {
        val byteCol = getText(row).substring(0, col).toByteArray(charset).size
        insert(row, byteCol, cs.toString().toByteArray(charset))
    }

    override fun delete(row: Int, col: Int, cs: CharSequence) {
        val byteCol = getText(row).substring(0, col).toByteArray(charset).size
        delete(row, byteCol, cs.toString().toByteArray(charset).size)
    }

    override fun getText(row: Int): CharSequence {
        val r = get(row)
        return String(get(row), charset)
    }

    override fun insert(row: Int, rawCol: Int, bytes: ByteArray) {
        val adjustedCol = if (row == 0) rawCol + bom.size else rawCol
        pt.insert(index.serial(row, adjustedCol), bytes)
        index.insert(row, adjustedCol, bytes)
    }

    override fun delete(row: Int, rawCol: Int, rawLen: Int) {
        val adjustedCol = if (row == 0) rawCol + bom.size else rawCol
        val t = index.serial(row, adjustedCol)
        pt.delete(index.serial(row, adjustedCol), rawLen)
        index.delete(row, adjustedCol, rawLen)
    }

    override fun get(row: Int, rawCol: Int, rawLen: Int): ByteArray {
        val adjustedCol = if (row == 0) rawCol + bom.size else rawCol
        return pt.get(index.serial(row, adjustedCol), rawLen)!!
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


    override fun getText(row: Int, rawCol: Int, rawLen: Int): CharSequence {
        return String(get(row, rawCol, rawLen), charset)
    }

    override fun rows(): Int = index.rowSize()

    override fun rawSize(): Long = pt.length() - bom.size

    override fun serial(row: Int, col: Int): Long = index.serial(row, col)

    override fun pos(serial: Long): Caret {
        val ret = index.pos(serial)
        return Caret(ret[0], ret[1])
    }

    override fun charset(): Charset = charset

    override fun rowEnding(): NewLine = newLine

    override fun bom(): ByteArray = bom.copyOf()
}
