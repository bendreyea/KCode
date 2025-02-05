package org.editor.presentation.components.textpane

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.editor.ApplicationScope
import org.editor.application.Caret
import org.editor.application.TextEdit
import org.editor.syntax.HighlightInterval
import org.editor.syntax.SyntaxHighlighter
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Provides the “document model” for our custom KTextPane,
 * plus a place to do highlighting.
 */
class TextPaneContent(private val textEdit: TextEdit) {
    private val listeners = mutableListOf<(Int, Int) -> Unit>()
    private val parser = SyntaxHighlighter()
    private val channel = Channel<Pair<IntRange, (Int) -> String>>(capacity = 100)
    private val numConsumers = Runtime.getRuntime().availableProcessors()
    private val stateLock = ReentrantReadWriteLock()


    private var measureMaxLineWidth = 0

    init {
        repeat(numConsumers) {
            ApplicationScope.scope.launch {
                consumeRows(channel)
            }
        }
    }

    private suspend fun consumeRows(channel: Channel<Pair<IntRange, (Int) -> String>>) {
        for ((range, getTextRow) in channel) {
            try {
                val minLine = range.first
                val maxLine = range.last

                val chunkIndexStart = minLine / parser.chunkSize
                val chunkIndexEnd   = maxLine / parser.chunkSize

                // For each chunk in that range, invoke parser.onLineChanged
                for (chunkIndex in chunkIndexStart..chunkIndexEnd) {
                    // We can pass the "start line" of the chunk OR simply pass minLine in that chunk.
                    // The parser's chunk logic doesn't care so long as it can compute chunkIndex internally.
                    val lineForThisChunk = chunkIndex * parser.chunkSize
                    parser.onLineChanged(lineForThisChunk, getTextRow, ::rows)
                }

                notifyChange(minLine, maxLine)

            } catch (e: Exception) {
                produceRows(range) { lineIndex -> getText(lineIndex) }
            }
        }
    }

    private suspend fun produceRows(range: IntRange, getTextRow: (Int) -> String) {
        channel.send(Pair(range, getTextRow))
    }


    fun rows(): Int = textEdit.rows()

    fun getText(row: Int): String  {
        val text = textEdit.getText(row).trim('\n')
        if (text.length > measureMaxLineWidth) {
            measureMaxLineWidth = text.length
        }

        return text
    }

    fun getText(start: Caret, end: Caret): String = textEdit.getText(start, end)

    fun getMaxLineWidth() : Int  {
        return measureMaxLineWidth
    }

    private fun onTextChanged(from: Int, to : Int, totalRowsBeforeChange: Int) {
        stateLock.writeLock().lock()
        try {
            val currentTotalRows = rows()
            if (totalRowsBeforeChange != currentTotalRows) {
                // The number of rows has changed, so we need to reparse everything
                parser.resizeCheckpoints(currentTotalRows)
            }
        }
        finally {
            stateLock.writeLock().unlock()
        }

        val changedRange = from..to

        val subRanges = splitRange(changedRange, numConsumers / 2)

        // dispatch the parse range to our channel so that it runs in the background.
        subRanges.forEachIndexed { index, subRange ->
            ApplicationScope.scope.launch {
                produceRows(subRange) { lineIndex -> getText(lineIndex) }
            }
        }
    }

    fun getIntervalsForLine(row: Int): List<HighlightInterval> {
        stateLock.readLock().lock()
        try {
            return parser.getAllIntervals(row)
        }
        finally {
            stateLock.readLock().unlock()
        }
    }

    fun getTextContent(): String {
        // Return all text as a single string
        val sb = StringBuilder()
        for (r in 0 until rows()) {
            if (r > 0)
                sb.append("\n")

            sb.append(getText(r))
        }

        return sb.toString()
    }

    fun delete(row: Int, col: Int): Caret {
        val prevTotalRows = rows()
        val caret = textEdit.delete(row, col)
        onTextChanged(row, caret.row, prevTotalRows)

        return caret
    }

    fun backspace(row: Int, col: Int): Caret {
        val prevTotalRows = rows()
        val cursor = textEdit.backspace(row, col)
        onTextChanged(row, cursor.row, prevTotalRows)

        return cursor
    }

    fun insertChar(row: Int, col: Int, c: Char): Caret {
        val prevTotalRows = rows()
        val cursor = textEdit.insert(row, col, c.toString())
        onTextChanged(row, cursor.row, prevTotalRows)

        return cursor
    }

    fun insert(row: Int, col: Int, text: String): Caret {
        val prevRows = rows()
        val cursor = textEdit.insert(row, col, text)
        onTextChanged(row, cursor.row, prevRows)

        return cursor
    }

    fun replace(row: Int, col: Int, len: Int, text: String): Caret {
        val prevRows = rows()
        val cursor = textEdit.replace(row, col, len, text)
        onTextChanged(row, cursor.row, prevRows)

        return cursor
    }

    fun addChangeListener(listener: (start: Int, end: Int) -> Unit) {
        listeners.add(listener)
    }

    fun removeChangeListener(listener: (start: Int, end: Int) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyChange(start: Int, end: Int) {
        listeners.forEach { it.invoke(start, end) }
    }

    private fun splitRange(range: IntRange, parts: Int): List<IntRange> {
        require(parts > 0) { "Number of parts must be greater than zero." }

        val totalElements = range.last - range.first + 1
        val baseSize = totalElements / parts
        val remainder = totalElements % parts

        val subRanges = mutableListOf<IntRange>()
        var start = range.first

        for (i in 0 until parts) {
            val extra = if (i < remainder) 1 else 0
            val end = start + baseSize + extra - 1
            subRanges.add(start..minOf(end, range.last))
            start = end + 1
        }

        return subRanges
    }
}