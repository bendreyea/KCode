package org.editor.presentation.components.textpane

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.editor.ApplicationScope
import org.editor.application.common.UserCaret
import org.editor.application.editor.EditorChangeListener
import org.editor.application.editor.EditorDocumentSnapshot
import org.editor.application.editor.TextEdit
import org.editor.syntax.highlighter.HighlightInterval
import org.editor.syntax.highlighter.IncrementalHighlighter
import org.editor.syntax.highlighter.ParseCacheManager
import org.editor.syntax.highlighter.Parser
import org.editor.syntax.intervalTree.Interval
import kotlin.coroutines.cancellation.CancellationException

/**
 * Provides the “document model” for our custom KTextPane,
 * plus a place to do highlighting.
 */
class TextPaneContent(private val textEdit: TextEdit) {
    private val listeners = mutableListOf<(Int, Int) -> Unit>()
    private val incrementalParser = IncrementalHighlighter(Parser(), ParseCacheManager())
    private var currentParseJob: Job? = null

    private var measureMaxLineWidth = 0

    init {
        textEdit.addEditorListener(object : EditorChangeListener {
            override fun onEditorChange(start: Int, end: Int, editorSnapshot: EditorDocumentSnapshot) {
                currentParseJob?.cancel()

                // Launch new job in the default dispatcher
                currentParseJob = ApplicationScope.scope.launch(Dispatchers.Default) {
                    val editRange = object : Interval {
                        override val start = start
                        override val end = end
                    }

                    // Tell the incremental highlighter to parse the changed region
                    incrementalParser.handleEdit(editRange, editorSnapshot)

                    // Once background parsing is done, switch back to Swing EDT
                    withContext(org.editor.SwingDispatchers.Swing) {
                        if (editorSnapshot.getVersion() == textEdit.getVersion()) {
                            notifyChange(start, end)
                        }
                    }
                }
            }
        })
    }

    fun rows(): Int = textEdit.rows()

    fun getText(row: Int): String  {
        val text = textEdit.getText(row)

        if (text.length > measureMaxLineWidth) {
            measureMaxLineWidth = text.length
        }

        return text
    }

    fun getText(start: UserCaret, end: UserCaret): String = textEdit.getText(start, end)

    fun getMaxLineWidth() : Int  {
        return measureMaxLineWidth
    }

    fun undo() : List<UserCaret> {
        return textEdit.undo()
    }

    fun redo(): List<UserCaret> {
        return textEdit.redo()
    }

    fun rowEnding() = textEdit.rowEnding()


    fun getIntervalsForLine(row: Int): List<HighlightInterval> {
        val being = textEdit.serial(row, 0).toInt()
        val end = textEdit.serial(row, textEdit.getText(row).length).toInt()
        return incrementalParser.getAllIntervals(row, being, end)
    }

    fun getTextContent(): String {
        // Return all text as a single string
        val sb = StringBuilder()
        for (r in 0 until rows()) {
            sb.append(getText(r))
        }

        return sb.toString()
    }

    fun delete(row: Int, col: Int): UserCaret {
        val caret = textEdit.delete(row, col)
        return caret
    }

    fun backspace(row: Int, col: Int): UserCaret {
        val cursor = textEdit.backspace(row, col)
        return cursor
    }

    fun insert(row: Int, col: Int, text: String): UserCaret {
        val cursor = textEdit.insert(row, col, textEdit.rowEnding().unify(text))
        return cursor
    }

    fun replace(row: Int, col: Int, len: Int, text: String): UserCaret {
        val cursor = textEdit.replace(row, col, len, text)
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
}

/**
 * Safe cancellation that ensures the previous job is fully canceled before launching a new one.
 */
private suspend fun Job?.cancelAndJoinSafely() {
    this?.let {
        it.cancel()
        try {
            it.join()
        } catch (e: CancellationException) {
            e.printStackTrace()
        }
    }
}