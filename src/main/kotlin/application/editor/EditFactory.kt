package org.editor.application.editor

import org.editor.application.common.UserCaret
import org.editor.application.doc.Document

/**
 * A lightweight factory for creating Edit instances.
 */
object EditFactory {
    fun createInsert(row: Int, col: Int, text: CharSequence, lineSeparator: String): Edit.Insert {
        val textString = if (text is String) text else text.toString()
        val from = UserCaret(row, col)
        // Find the last occurrence of the platform line separator
        val indexOfLineSep = textString.lastIndexOf(lineSeparator)
        val to = if (indexOfLineSep < 0) {
            // The inserted text has no new line.
            UserCaret(row, col + textString.length)
        } else {
            // If the text spans multiple lines, split on the platform separator.
            val lines = textString.split(lineSeparator)
            val additionalRows = lines.size - 1
            val newRow = row + additionalRows
            val newCol = lines.last().length
            UserCaret(newRow, newCol)
        }

        return Edit.Insert(from, to, textString)
    }

    fun createDelete(row: Int, col: Int, text: CharSequence): Edit.Delete {
        val textString = if (text is String) text else text.toString()
        return Edit.Delete(UserCaret(row, col), textString)
    }

    fun createBackspace(row: Int, col: Int, text: String, doc: Document): Edit.Delete {
        val newRow = row - countRowBreak(text, doc.lineSeparator().str())
        val newCol = if (row == newRow) {
            col - text.length
        } else {
            val index = text.indexOf(doc.lineSeparator().str())
            val startOffset = if (index >= 0) index + doc.lineSeparator().str().length else text.length
            (doc.getText(newRow).length - startOffset).coerceAtLeast(0)
        }

        return Edit.Delete(
            UserCaret(newRow, newCol),
            UserCaret(row, col),
            text
        )
    }

    private fun countRowBreak(text: String, lineSep: String): Int =
        text.split(lineSep).size - 1
}
