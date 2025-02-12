package org.editor.application.editor

import org.editor.application.TextRowIndex
import org.editor.application.common.UserCaret
import org.editor.application.doc.DocumentSnapshot

/**
 * A snapshot of the editor document at a given point in time.
 */
data class EditorDocumentSnapshot(
    private val version: Int,
    private val index: TextRowIndex,
    private val doc: DocumentSnapshot,
    private val dryBuffer: Map<Int, String>,   // unflushed lines
) {
    /*
     * Get the number of rows in the document.
     */
    fun rows(): Int {
        val docRows = doc.rows()
        val maxRowInDry = dryBuffer.keys.maxOrNull() ?: -1
        return maxOf(docRows, maxRowInDry + 1)
    }

    /**
     * Get the text at the specified row.
     */
    fun getText(row: Int): String {
        // If row is in dryBuffer, return that
        return dryBuffer[row] ?: doc.getText(row).toString()
    }

    /*
     * Get the offset.
     */
    fun serial(row: Int, col: Int): Long {
        return if (dryBuffer.containsKey(row)) {
            (dryBuffer[row]?.length!! + col).toLong()
        } else {
//            println("serial: $row, $col -> ${index.rowLengths()[0]}")
            index.serial(row, col)
        }
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