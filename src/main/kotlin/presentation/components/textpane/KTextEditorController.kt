package org.editor.presentation.components.textpane

import org.editor.application.common.UserCaret
import java.awt.FontMetrics
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.*
import java.util.concurrent.CopyOnWriteArrayList

class KTextEditorController(
    private val textPane: TextPaneContent,
    private val fontMetrics: FontMetrics,
    private val editorTheme: EditorTheme,
    private val getHeight: () -> Int,
    private val getWidth: () -> Int,
    private val repaintCallback: (UserCaret, UserCaret) -> Unit,
    private val repaintVisibleRegion: () -> Unit
) : KeyAdapter(), MouseListener, MouseMotionListener {

    // List of CaretListeners
    private val caretListeners = CopyOnWriteArrayList<CaretListener>()

    // --------------------- Scrolling & Layout ---------------------
    var scrollX: Int = 0
    var scrollY: Int = 0

    // --------------------- Caret & Selection ----------------------
    var caret = UserCaret(0, 0)
    var selectionStart: UserCaret? = null
    var selectionEnd: UserCaret? = null
    private var isDragging = false

    private val PAGE_STEP_LINES = 5

    val hasSelection: Boolean
        get() = selectionStart != null && selectionEnd != null && selectionStart != selectionEnd

    fun clearSelection() {
        selectionStart = null
        selectionEnd = null
    }

    // Direction vectors: right, down, left, up
    private val directions = arrayOf(
        0 to 1,  // RIGHT
        1 to 0,  // DOWN
        0 to -1, // LEFT
        -1 to 0  // UP
    )

    fun addCaretListener(listener: CaretListener) {
        caretListeners.add(listener)
    }

    // Method to remove a CaretListener
    fun removeCaretListener(listener: CaretListener) {
        caretListeners.remove(listener)
    }

    // Notify all CaretListeners about caret movement
    private fun notifyCaretListeners() {
        caretListeners.forEach { it.caretMoved(caret) }
    }

    // Modify caret updates to notify listeners
    private fun updateCaret(newCaret: UserCaret) {
        caret = newCaret
        notifyCaretListeners()
    }

    // --------------------- Keyboard Handling ---------------------
    override fun keyPressed(e: KeyEvent) {
        when (e.keyCode) {
            KeyEvent.VK_LEFT  -> moveCaret(e.isShiftDown, directions[2])
            KeyEvent.VK_RIGHT -> moveCaret(e.isShiftDown, directions[0])
            KeyEvent.VK_UP    -> moveCaret(e.isShiftDown, directions[3])
            KeyEvent.VK_DOWN  -> moveCaret(e.isShiftDown, directions[1])

            KeyEvent.VK_HOME -> {
                if (e.isShiftDown)
                    ensureSelectionStart()

                updateCaret(UserCaret(0, 0))
                if (!e.isShiftDown)
                    clearSelection()

                repaintVisibleRegion()
            }

            KeyEvent.VK_END -> {
                if (e.isShiftDown)
                    ensureSelectionStart()

                val lineText = textPane.getText(caret.row)
                updateCaret(caret.copy(col = lineText.length))

                if (!e.isShiftDown)
                    clearSelection()

                repaintVisibleRegion()
            }

            KeyEvent.VK_PAGE_UP   -> moveCaret(e.isShiftDown, directions[3], PAGE_STEP_LINES)
            KeyEvent.VK_PAGE_DOWN -> moveCaret(e.isShiftDown, directions[1], PAGE_STEP_LINES)

            KeyEvent.VK_DELETE -> {
                if (hasSelection) {
                    deleteSelection()
                } else {
                    textPane.delete(caret.row, caret.col)
                }
                repaintVisibleRegion()
            }

            // ---------- Clipboard shortcuts ----------
            KeyEvent.VK_C -> {
                if (e.isControlDown || e.isMetaDown)
                    copySelection()
            }
            KeyEvent.VK_X -> {
                if (e.isControlDown || e.isMetaDown)
                    cutSelection()
            }
            KeyEvent.VK_V -> {
                if (e.isControlDown || e.isMetaDown)
                    pasteClipboard()
            }

            KeyEvent.VK_Z -> {
                if (e.isControlDown || e.isMetaDown) {
                    undo() // CTRL+Z -> Undo
                }
            }
            KeyEvent.VK_Y -> {
                if (e.isControlDown || e.isMetaDown)
                    redo() // CTRL+Y -> Redo
            }

            KeyEvent.VK_ENTER -> {
                if (hasSelection) {
                    deleteSelection()
                }
                val insertCaret = textPane.insert(caret.row, caret.col, textPane.rowEnding().str())

                updateCaret(insertCaret)
                clearSelection()

                repaintVisibleRegion()
            }

             KeyEvent.VK_BACK_SPACE -> {
                 // TODO: have a huge bug with backspace method
                 if (hasSelection) {
                     deleteSelection()
                 } else {
                     if (caret.col == 0) {
                         // If the current line is empty and not the last line, delete the line
                         if (textPane.getText(caret.row).isEmpty() && caret.row < textPane.rows() - 1) {
                             val newPos = textPane.delete(caret.row, caret.col)
                             updateCaret(newPos)
                         } else if (caret.row > 0) {
                             // Move caret to the end of the previous line
                             val prevRow = caret.row - 1
                             val prevCol = textPane.getText(prevRow).length
                             // Optionally, join lines if needed
                             val newPos = textPane.delete(prevRow, prevCol)
                             updateCaret(newPos)
                         }
                     } else {
                         // Delete the character before the caret
                         val newCol = caret.col - 1
                         textPane.delete(caret.row, newCol)
                         updateCaret(caret.withCol(newCol))
                     }

                     clearSelection()
                     repaintVisibleRegion()
                 }
             }
        }
    }

    override fun keyTyped(e: KeyEvent) {
        if (e.keyChar == KeyEvent.CHAR_UNDEFINED)
            return

        if (e.keyChar.isISOControl())
            return

        if (hasSelection) {
            deleteSelection()
        }
        updateCaret(textPane.insert(caret.row, caret.col, e.keyChar.toString()))
        clearSelection()

        repaintVisibleRegion()
    }

    // --------------------- Mouse Handling ---------------------
    override fun mousePressed(e: MouseEvent) {
        handleMousePressed(e)
    }

    override fun mouseDragged(e: MouseEvent) {
        handleMouseDragged(e)
    }

    override fun mouseReleased(e: MouseEvent) {
        isDragging = false
        selectionStart?.let { start ->
            repaintCallback(start, caret)
        }
    }

    override fun mouseClicked(e: MouseEvent) { }
    override fun mouseEntered(e: MouseEvent) { }
    override fun mouseExited(e: MouseEvent) { }
    override fun mouseMoved(e: MouseEvent) { }

    private fun handleMousePressed(e: MouseEvent) {
        isDragging = true
        val prevCursor = moveMouseCaret(e)
        val (start, end) = orderedCursors(selectionStart ?: prevCursor, selectionEnd ?: caret)
        selectionStart = caret
        selectionEnd = null
        repaintCallback(start, end)
    }

    private fun handleMouseDragged(e: MouseEvent) {
        val prevCursor = moveMouseCaret(e)
        selectionEnd = caret
        repaintCallback(prevCursor, caret)
    }

    private fun moveMouseCaret(e: MouseEvent): UserCaret {
        val prevCursor = caret.copy()

        // Adjust y-coordinate with scrollY to get the correct row
        val adjustedY = e.y + scrollY
        val row = (adjustedY / fontMetrics.height).coerceIn(0, textPane.rows() - 1)

        // Adjust x-coordinate with scrollX to get the correct column
        val adjustedX = e.x + scrollX - (editorTheme.gutterWidth + editorTheme.horizontalPadding)
        val col = measureClickOffset(textPane.getText(row), adjustedX)

        updateCaret(UserCaret(row, col))
        return prevCursor
    }

    private fun measureClickOffset(line: String, mouseX: Int): Int {
        var widthSoFar = 0
        for ((index, ch) in line.withIndex()) {
            val charW = fontMetrics.charWidth(ch)
            if (mouseX < widthSoFar + charW / 2) {
                return index
            }

            widthSoFar += charW
        }

        return line.length
    }

    // --------------------- Moving the Caret & Scrolling ---------------------
    private fun moveCaret(shiftDown: Boolean, direction: Pair<Int, Int>, step: Int = 1) {
        if (shiftDown)
            ensureSelectionStart()

        var newRow = (caret.row + direction.first * step).coerceIn(0, textPane.rows() - 1)
        var newCol = caret.col + direction.second * step

        // Handle going left beyond row start
        if (newCol < 0) {
            while (newCol < 0 && newRow > 0) {
                newRow--
                newCol += textPane.getText(newRow).length
            }

            if (newRow < 0) {
                newRow = 0
                newCol = 0
            }
        }
        // Handle going right beyond row end
        else if (newCol > textPane.getText(newRow).length) {
            while (newCol > textPane.getText(newRow).length && newRow < textPane.rows() - 1) {
                newCol -= textPane.getText(newRow).length
                newRow++
            }
            if (newRow >= textPane.rows()) {
                newRow = textPane.rows() - 1
                newCol = textPane.getText(newRow).length
            }
        }

        newRow = newRow.coerceIn(0, textPane.rows() - 1)
        newCol = newCol.coerceIn(0, textPane.getText(newRow).length)
        val prev = caret.copy()
        updateCaret(caret.copy(row = newRow, col = newCol))
        if (!shiftDown)
            clearSelection()

        repaintCallback(prev, caret)
    }

    // --------------------- Undo / Redo ---------------------
    fun undo() {
        val caretPositions = textPane.undo()
        if (caretPositions.isNotEmpty()) {
            updateCaret(caretPositions.first())
            repaintVisibleRegion()
        }
    }

    fun redo() {
        val caretPositions = textPane.redo()
        if (caretPositions.isNotEmpty()) {
            updateCaret(caretPositions.first())
            repaintVisibleRegion()
        }
    }

    // --------------------- Selection & Clipboard ---------------------
    private fun copySelection() {
        if (!hasSelection) return
        val selText = getSelectedText()
        val cb = Toolkit.getDefaultToolkit().systemClipboard
        val contents = StringSelection(selText)
        cb.setContents(contents, contents)
    }

    private fun cutSelection() {
        if (!hasSelection)
            return

        copySelection()
        deleteSelection()
    }

    fun getSelectedText(): String {
        if (!hasSelection)
            return ""

        val (start, end) = orderedCursors(selectionStart!!, selectionEnd!!)
        return textPane.getText(start, end)
    }

    private fun pasteClipboard() {
        val cb = Toolkit.getDefaultToolkit().systemClipboard
        val data = cb.getContents(this) ?: return
        if (data.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            val text = data.getTransferData(DataFlavor.stringFlavor) as String
            if (hasSelection) {
                deleteSelection()
            }
            val newCaret = textPane.insert(caret.row, caret.col, text)
            updateCaret(newCaret)
            clearSelection()
            repaintVisibleRegion()
        }
    }

    private fun deleteSelection() {
        if (!hasSelection)
            return

        val (start, end) = orderedCursors(selectionStart!!, selectionEnd!!)
        val lengthToRemove = textPane.getText(start, end).length
        textPane.replace(start.row, start.col, lengthToRemove, "")
        updateCaret(start)
        clearSelection()
        repaintCallback(start, end)
        repaintVisibleRegion()
    }

    private fun ensureSelectionStart() {
        if (selectionStart == null) {
            selectionStart = caret
        }
    }

    fun orderedCursors(c1: UserCaret, c2: UserCaret): Pair<UserCaret, UserCaret> {
        return if (c1 < c2) c1 to c2 else c2 to c1
    }
}
