package org.editor.presentation.components.textpane

import java.awt.FontMetrics
import java.awt.Graphics2D
import kotlin.math.max
import kotlin.math.min

class KTextEditorPainter(
    private val textPane: TextPaneContent,
    private val controller: KTextEditorController,
    private val fontMetrics: FontMetrics,
    private val editorTheme: EditorTheme) {

    fun paint(g2: Graphics2D, width: Int, height: Int) {
        paintBackground(g2, width, height)
        g2.translate(-controller.scrollX, -controller.scrollY)

        paintGutter(g2, height)
        paintLines(g2, width, height)
        if (controller.hasSelection)
            paintSelection(g2)

        paintCaret(g2)
    }

    private fun paintBackground(g2: Graphics2D, width: Int, height: Int) {
        g2.color = editorTheme.canvasBackgroundColor
        g2.fillRect(0, 0, width, height)
    }

    private fun paintGutter(g2: Graphics2D, height: Int) {
        g2.color = editorTheme.gutterColor
        g2.fillRect(0, 0, editorTheme.gutterWidth, height)
    }

    private fun paintLines(g2: Graphics2D, widthWindow: Int, heightWindow: Int) {
        val clip = g2.clipBounds
        // Determine which rows are visible in the clip
        val firstRow = max(0, clip.y / fontMetrics.height)
        val lastRow = min(textPane.rows() - 1, (clip.y + clip.height) / fontMetrics.height)

        // Draw the visible rows
        for (row in firstRow..lastRow) {
            paintLine(g2, row)
        }

        // Fill the rest of the area after the last row if needed
        val totalRowsHeight = textPane.rows() * fontMetrics.height

        if (clip.y + clip.height > totalRowsHeight) {
            g2.color = editorTheme.canvasBackgroundColor // Use the background color
            g2.fillRect(
                editorTheme.gutterWidth, // Start after the gutter
                totalRowsHeight, // Start drawing after the last drawn line
                widthWindow - editorTheme.gutterWidth, // Width of the remaining area
                (clip.y + clip.height) - totalRowsHeight // Height of the remaining area
            )
        }

        // If lines were deleted, clear any remnants outside the visible area
        if (textPane.rows() * fontMetrics.height < heightWindow) {
            g2.color = editorTheme.canvasBackgroundColor
            g2.fillRect(
                editorTheme.gutterWidth, // Start after the gutter
                textPane.rows() * fontMetrics.height, // Start clearing after the last line's height
                widthWindow - editorTheme.gutterWidth, // Width of the editor
                heightWindow - textPane.rows() * fontMetrics.height // Height of the remaining blank space
            )
        }
    }


    private fun paintLine(g2: Graphics2D, row: Int) {
        val lineText = textPane.getText(row)
        val yPos = row * fontMetrics.height

        // Draw line number (gutter)
        g2.color = editorTheme.gutterTextColor
        val lineNumStr = (row + 1).toString()
        val lineNumWidth = fontMetrics.stringWidth(lineNumStr)
        g2.drawString(
            lineNumStr,
            editorTheme.gutterWidth - lineNumWidth - 5,
            yPos + fontMetrics.height - fontMetrics.descent
        )

        val intervals = textPane.getIntervalsForLine(row)

        var currentX = editorTheme.gutterWidth + editorTheme.horizontalPadding
        var lastEnd = 0

        // Draw tokens (intervals) one by one
        for (interval in intervals) {
            // Plain text up to interval.start
            if (interval.start > lastEnd) {
                val plainText = lineText.substring(
                    lastEnd.coerceIn(0, lineText.length),
                    interval.start.coerceIn(0, lineText.length)
                )
                g2.color = editorTheme.textColor
                g2.drawString(plainText, currentX, yPos + fontMetrics.height - fontMetrics.descent)
                currentX += fontMetrics.stringWidth(plainText)
            }

            // Highlighted interval text
            val tokenText = lineText.substring(
                interval.start.coerceIn(0, lineText.length),
                interval.end.coerceIn(0, lineText.length)
            )
            val color = editorTheme.tokenColors[interval.type] ?: editorTheme.textColor
            g2.color = color
            g2.drawString(tokenText, currentX, yPos + fontMetrics.height - fontMetrics.descent)
            currentX += fontMetrics.stringWidth(tokenText)

            lastEnd = interval.end
        }

        // Plain text after last interval
        if (lastEnd < lineText.length) {
            val plainText = lineText.substring(lastEnd)
            g2.color = editorTheme.textColor
            g2.drawString(plainText, currentX, yPos + fontMetrics.height - fontMetrics.descent)
        }
    }


    private fun paintSelection(g2: Graphics2D) {
        val (start, end) = controller.orderedCursors(controller.selectionStart!!, controller.selectionEnd!!)
        for (row in start.row..end.row) {
            val lineText = textPane.getText(row)
            val lineY = row * fontMetrics.height
            val startCol = if (row == start.row) start.col else 0
            val endCol = if (row == end.row) end.col else lineText.length

            if (startCol <= endCol && startCol <= lineText.length) {
                val x1 = editorTheme.gutterWidth + editorTheme.horizontalPadding +
                        fontMetrics.stringWidth(lineText.substring(0, startCol))
                val x2 = editorTheme.gutterWidth + editorTheme.horizontalPadding +
                        fontMetrics.stringWidth(lineText.substring(0, endCol))

                g2.color = editorTheme.selectionColor
                g2.fillRect(x1, lineY, max(1, x2 - x1), fontMetrics.height)
            }
        }
    }

    private fun paintCaret(g2: Graphics2D) {
        if (controller.caret.row !in 0 until textPane.rows())
            return

        val lineText = textPane.getText(controller.caret.row)
        val caretX = editorTheme.gutterWidth + editorTheme.horizontalPadding +
                fontMetrics.stringWidth(lineText.take(controller.caret.col))
        val caretY = controller.caret.row * fontMetrics.height

        g2.color = editorTheme.caretColor
        g2.drawLine(caretX, caretY, caretX, caretY + fontMetrics.height)
    }
}