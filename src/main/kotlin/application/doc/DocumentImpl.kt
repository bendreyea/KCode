package org.editor.application.doc

import org.editor.application.*
import org.editor.application.common.LineSeparator
import org.editor.core.PieceTable
import org.editor.core.TextBuffer
import java.nio.charset.Charset

/**
 * Implementation of the [Document] interface.
 */
class DocumentImpl(
    pt: TextBuffer,
    charset: Charset,
    bom: ByteArray,
    lineSeparator: LineSeparator,
    index: RowIndex
) : BaseDocument(pt, charset, bom, lineSeparator, index), Document {

    companion object {
        /**
         * Creates a new [DocumentImpl] instance with configurable charset, BOM, and newline.
         *
         * @param charset The [Charset] used for encoding/decoding text (default: UTF-8).
         * @param bom The Byte Order Mark to use (default: empty).
         * @param lineSeparator The newline ending to use (default: platform default).
         */
        fun create(
            charset: Charset = Charset.forName("UTF-8"),
            bom: ByteArray = ByteArray(0),
            lineSeparator: LineSeparator = LineSeparator.platform
        ): DocumentImpl {
            // Create a RowIndex that uses the newline ending’s bytes.
            val rowIndex = RowIndex.create(lineSeparator.str().toByteArray())
            val pt = PieceTable.create()
            return DocumentImpl(pt, charset, bom, lineSeparator, rowIndex)
        }
    }

    private val listeners = mutableListOf<DocumentChangeListener>()

    override fun addListener(listener: DocumentChangeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: DocumentChangeListener) {
        listeners.remove(listener)
    }

    private fun notifyListeners(event: DocumentSnapshot) {
        listeners.forEach { it.onDocumentChange(event) }
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
        if (deleteBytes.contentEquals(lineSeparator.bytes()) && row > 0 && col == 0) {
            val rowLens = index.rowLengths() // snapshot of row lengths (in bytes)
            // Ensure the previous row contains at least the newline bytes.
            val prevRowLen = rowLens[row - 1]
            require(prevRowLen >= lineSeparator.bytes().size) {
                "Previous row length must be at least ${lineSeparator.bytes().size} if newline is present; found $prevRowLen"
            }
            // The deletion should remove the newline sequence at the end of the previous row.
            val bytePos = index.serial(row - 1, prevRowLen - lineSeparator.bytes().size)
            pt = pt.withDelete(bytePos, lineSeparator.bytes().size)
            index.delete(row - 1, prevRowLen - lineSeparator.bytes().size, lineSeparator.bytes().size)
            notifyListeners(DocumentSnapshot(pt, charset, bom, lineSeparator, index.snapshot()))
        } else {
            val byteCol = charOffsetToByteOffset(row, col)
            val bytePos = index.serial(row, byteCol)
            pt = pt.withDelete(bytePos, deleteBytes.size)
            index.delete(row, byteCol, deleteBytes.size)
            notifyListeners(DocumentSnapshot(pt, charset, bom, lineSeparator, index.snapshot()))
        }
    }

    private fun insert(row: Int, rawCol: Int, bytes: ByteArray) {
        val adjustedCol = if (row == 0) rawCol + bom.size else rawCol
        pt = pt.withInsert(index.serial(row, adjustedCol), bytes)
        index.insert(row, adjustedCol, bytes)
        notifyListeners(DocumentSnapshot(pt, charset, bom, lineSeparator, index.snapshot()))
    }
}
