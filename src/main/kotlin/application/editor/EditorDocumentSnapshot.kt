package org.editor.application.editor

import org.editor.application.TextRowIndex
import org.editor.application.common.LineSeparator
import org.editor.application.common.UserCaret
import org.editor.application.doc.DocumentSnapshot

/**
 * A snapshot of the editor document at a given point in time.
 */
class EditorDocumentSnapshot(
    private val version: Int,
    val index: TextRowIndex,
    private val doc: DocumentSnapshot?,
    private val dryBuffer: Map<Int, String>,   // unflushed lines
) {
    private val virtualRowCount: Int by lazy {
        val maxDryBufferRow = dryBuffer.keys.maxOrNull() ?: -1
        maxOf(index.rowSize(), maxDryBufferRow + 1)
    }

    /*
     * Get the number of rows in the document.
     */
    fun rows(): Int = virtualRowCount

    /**
     * Get the text at the specified row.
     */
    fun getText(row: Int): String {
        // Return dryBuffer row if available.
        if (dryBuffer.containsKey(row)) {
            return dryBuffer[row]!!
        }
        // If the row is beyond the underlying index, return an empty string.
        if (row >= index.rowSize()) {
            return ""
        }
        return doc?.getText(row).toString()
            .replace(doc?.rowEnding()?.str() ?: LineSeparator.platform.str(), "")
            ?: ""
    }

    /*
     * Get the offset.
     */
    fun serial(row: Int, col: Int): Long {
        if (row < 0 || row >= virtualRowCount) {
            throw IllegalArgumentException("Row index out of bounds: $row")
        }

        var sum = 0L
        for (i in 0 until row) {
            val rowLength = when {
                dryBuffer.containsKey(i) -> dryBuffer[i]!!.length
                i < index.rowSize() -> index.getRowLength(i)
                else -> 0  // Virtual extra row is treated as empty.
            }
            sum += rowLength.toLong()
        }

        val currentRowLength = when {
            dryBuffer.containsKey(row) -> dryBuffer[row]!!.length
            row < index.rowSize() -> index.getRowLength(row)
            else -> 0
        }

        if (col < 0 || col > currentRowLength) {
            throw IllegalArgumentException("Column index out of bounds: $col")
        }

        return sum + col
    }

    /**
     * Get the position.
     */
    fun pos(serial: Long): UserCaret {
        val (row, col) = index.pos(serial)
        return UserCaret(row, col)
    }

    /**
     * Get version.
     */
    fun getVersion(): Int = version
}