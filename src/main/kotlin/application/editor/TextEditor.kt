package org.editor.application.editor

import org.editor.application.TextRowIndex
import org.editor.application.common.LineSeparator
import org.editor.application.common.UserCaret
import org.editor.application.doc.Document
import org.editor.application.doc.DocumentSnapshot
import org.editor.application.editor.Edit.*
import java.nio.charset.Charset
import java.util.*
import kotlin.math.min

/**
 * A production-ready [TextEdit] implementation.
 *
 * This class maintains a queue of unflushed edits, applies them to the underlying
 * [Document], and supports undo/redo operations. It also keeps an in-memory “dry run”
 * buffer to provide a responsive UI while typing.
 *
 * @property doc The underlying document.
 */
class TextEditImpl(
    private val doc: Document
) : TextEdit {

    //region Public API

    override fun insert(row: Int, col: Int, text: CharSequence): UserCaret {
        if (text.isEmpty()) return UserCaret(row, col)

        val edit = createInsertEdit(row, col, text)
        pushEdit(edit)
        notifyEditorListeners(row, edit.to().row)
        return edit.to()
    }

    override fun delete(row: Int, col: Int, len: Int): UserCaret {
        val toDelete = textRight(row, col, len).joinToString("")
        val unifiedText = doc.lineSeparator().unify(toDelete)
        val edit = createDeleteEdit(row, col, unifiedText)
        pushEdit(edit)
        notifyEditorListeners(row, edit.min().row)
        return edit.min()
    }

    override fun backspace(row: Int, col: Int, len: Int): UserCaret {
        val toDelete = textLeft(row, col, len).joinToString("")
        val edit = createBackspaceEdit(row, col, toDelete)
        pushEdit(edit)
        notifyEditorListeners(row, edit.min().row)
        return edit.min()
    }

    override fun replace(row: Int, col: Int, len: Int, text: CharSequence): UserCaret {
        if (len == 0) return insert(row, col, text)

        return if (len > 0) {
            // Delete then insert
            val delText = textRight(row, col, len).joinToString("")
            val del = createDeleteEdit(row, col, delText)
            pushEdit(del)
            flush() // flush to update the index

            val newCaret = UserCaret(row, col)
            val ins = createInsertEdit(newCaret.row, newCaret.col, text)
            pushEdit(ins)
            flush()
            notifyEditorListeners(newCaret.row, ins.to().row)
            ins.to()
        } else {
            // Backspace then insert
            val absLen = -len
            val delText = textLeft(row, col, absLen).joinToString("")
            val bs = createBackspaceEdit(row, col, delText)
            pushEdit(bs)
            flush()

            val newCaret = bs.min()
            val ins = createInsertEdit(newCaret.row, newCaret.col, text)
            pushEdit(ins)
            flush()
            notifyEditorListeners(newCaret.row, ins.to().row)
            ins.to()
        }
    }

    override fun undo(): List<UserCaret> {
        flush()
        val edit = undoLast() ?: return emptyList()
        return when (edit) {
            is ConcreteEdit -> listOf(edit.to())
            is CompoundEdit -> listOf(edit.edits.last().to())
        }
    }

    override fun redo(): List<UserCaret> {
        flush()
        val edit = redoLast() ?: return emptyList()
        return when (edit) {
            is ConcreteEdit -> listOf(edit.to())
            is CompoundEdit -> listOf(edit.edits.last().to())
        }
    }

    override fun getText(row: Int): String {
        // Update dryBuffer if there are pending edits
        if (editQueue.isNotEmpty() && dryBuffer.isEmpty()) {
            applyEditsInMemory()
        }
        return dryBuffer[row] ?: getDocText(row)
    }

    override fun getText(start: UserCaret, end: UserCaret): String {
        var (sRow, sCol) = start
        var (eRow, eCol) = end

        if (end < start) {
            sRow = end.row
            sCol = end.col
            eRow = start.row
            eCol = start.col
        }

        return buildString {
            for (i in sRow..eRow) {
                var rowText = getText(i)
                if (i == eRow) {
                    rowText = rowText.substring(0, min(eCol, rowText.length))
                }
                if (i == sRow) {
                    rowText = rowText.substring(min(sCol, rowText.length))
                }
                append(rowText)
            }
        }
    }

    override fun rows(): Int = doc.rows()
    override fun charset(): Charset = doc.charset()
    override fun rowEnding(): LineSeparator = doc.lineSeparator()

    override fun getVersion(): Int = version

    override fun serial(row: Int, col: Int): Long {
        return dryBuffer[row]?.let { (it.length + col).toLong() } ?: index.serial(row, col)
    }

    override fun addEditorListener(listener: EditorChangeListener) {
        editorListeners.add(listener)
    }

    fun removeEditorListener(listener: EditorChangeListener) {
        editorListeners.remove(listener)
    }

    //endregion

    //region Internal State & Helpers

    // Edit queues and buffers
    private val editQueue: Deque<Edit> = ArrayDeque()
    private val undo: Deque<Edit> = ArrayDeque()
    private val redo: Deque<Edit> = ArrayDeque()
    private val dryBuffer: MutableMap<Int, String> = mutableMapOf()

    private var lastDocSnapshot: DocumentSnapshot? = null
    private var version = 0

    private val index = TextRowIndex.create(doc.lineSeparator().str())
    private val editorListeners = mutableListOf<EditorChangeListener>()

    init {
        doc.addListener { snapshot ->
            lastDocSnapshot = snapshot
            notifyEditorListeners(0, 0)
        }
    }

    /**
     * Notifies all editor listeners with an updated snapshot.
     *
     * @param start The starting row that changed.
     * @param end The ending row that changed.
     */
    private fun notifyEditorListeners(start: Int, end: Int) {
        val snapshot = lastDocSnapshot ?: return
        // Pass a copy of the dryBuffer to ensure immutability.
        val editorSnapshot = EditorDocumentSnapshot(
            version,
            index.snapshot(),
            snapshot,
            dryBuffer.toMap()
        )
        editorListeners.forEach { it.onEditorChange(start, end, editorSnapshot) }
    }

    /**
     * Returns the original document text for the given row.
     */
    private fun getDocText(row: Int): String = doc.getText(row).toString()

    /**
     * Flushes all pending edits: applies them to [doc], updates the undo/redo stacks,
     * and clears the dry run [dryBuffer].
     */
    private fun flush() {
        while (editQueue.isNotEmpty()) {
            val edit = editQueue.pop()
            applyToDocument(edit)
            undo.push(edit.flip())
            redo.clear()
        }
        dryBuffer.clear()
    }

    /**
     * Pushes a new edit into the queue. If the new edit can be merged with the last one,
     * merge them. Otherwise, flush any pending edits and push the new edit.
     */
    private fun pushEdit(edit: Edit) {
        // Try to merge with the last edit in the queue
        val lastEdit = editQueue.lastOrNull()
        if (lastEdit?.merge(edit) != null) {
            editQueue.removeLast()
            // Flush before adding merged edit to avoid inconsistent state.
            flush()
            editQueue.push(lastEdit.merge(edit)!!)
            return
        }
        // Flush pending edits before adding the new one.
        flush()
        editQueue.push(edit)

        // Immediately flush multi-line edits to avoid large in-memory buffering.
        when (edit) {
            is ConcreteEdit -> {
                if (edit.text().contains(rowEnding().str()))
                    flush()
            }
            is CompoundEdit -> {
                if (edit.edits.any { it.text().contains(rowEnding().str()) })
                    flush()
            }
        }
    }

    /**
     * Applies a single [edit] to the underlying document and updates the [index].
     */
    private fun applyToDocument(edit: Edit) {
        incrementVersion()
        when (edit) {
            is Insert -> {
                doc.insert(edit.min().row, edit.min().col, edit.text())
                index.insert(edit.min().row, edit.min().col, edit.text())
            }
            is Delete -> {
                doc.delete(edit.min().row, edit.min().col, edit.text())
                index.delete(edit.min().row, edit.min().col, edit.text().length)
            }
            is CompoundEdit -> edit.edits.forEach { applyToDocument(it) }
            is ConcreteEdit -> TODO()
        }
    }

    /**
     * Increments the document version.
     */
    private fun incrementVersion() {
        version++
    }

    /**
     * Applies all pending edits to the in-memory dry buffer.
     * This simulates the changes without modifying [doc].
     */
    private fun applyEditsInMemory() {
        dryBuffer.clear()
        editQueue.forEach { applyEditToDryBuffer(it) }
    }

    /**
     * Recursively applies an [edit] to the dry run [dryBuffer].
     */
    private fun applyEditToDryBuffer(edit: Edit) {
        incrementVersion()
        when (edit) {
            is Insert -> {
                val row = edit.from().row
                val original = dryBuffer[row] ?: getDocText(row)
                val newText = buildString {
                    append(original.substring(0, edit.min().col))
                    append(edit.text())
                    append(original.substring(edit.min().col.coerceAtMost(original.length)))
                }

                dryBuffer[row] = newText
            }
            is Delete -> {
                val row = edit.from().row
                val original = dryBuffer[row] ?: getDocText(row)
                val newText = buildString {
                    append(original.substring(0, edit.min().col))
                    append(
                        original.substring(
                            (edit.min().col + edit.text().length).coerceAtMost(original.length)
                        )
                    )
                }

                dryBuffer[row] = newText
            }
            is CompoundEdit -> edit.edits.forEach { applyEditToDryBuffer(it) }
            is ConcreteEdit -> TODO()
        }
    }

    /**
     * Undoes the last applied edit.
     */
    private fun undoLast(): Edit? {
        if (undo.isEmpty()) return null
        val edit = undo.pop()
        applyToDocument(edit)
        redo.push(edit.flip())
        return edit
    }

    /**
     * Redoes the last undone edit.
     */
    private fun redoLast(): Edit? {
        if (redo.isEmpty()) return null
        val edit = redo.pop()
        applyToDocument(edit)
        undo.push(edit.flip())
        return edit
    }

    /**
     * Retrieves [charsLen] characters to the right of the given ([row], [col]).
     */
    fun textRight(row: Int, col: Int, charsLen: Int): List<String> {
        var remainder = charsLen
        var currentCol = col
        val segments = mutableListOf<String>()

        for (i in row until doc.rows()) {
            val line = getText(i)
            if (currentCol >= line.length) break

            val text = line.substring(currentCol)
            if (text.isEmpty()) break

            if (remainder - text.length <= 0) {
                segments += text.substring(0, remainder)
                break
            }
            segments += text
            remainder -= text.length
            currentCol = 0
        }

        return segments
    }

    /**
     * Retrieves [byteLen] characters to the left of the given ([row], [col]).
     */
    fun textLeft(row: Int, col: Int, byteLen: Int): List<String> {
        var remainder = byteLen
        val segments: Deque<String> = ArrayDeque()
        var currentRow = row
        var currentCol = col

        if (currentCol == 0) {
            currentRow--
            if (currentRow < 0) return emptyList()
            currentCol = getText(currentRow).length
        }

        while (currentRow >= 0) {
            val line = getText(currentRow)
            val segment = line.substring(0, currentCol.coerceAtMost(line.length))
            if (remainder <= segment.length) {
                segments.addFirst(segment.substring(segment.length - remainder))
                break
            } else {
                segments.addFirst(segment)
                remainder -= segment.length
                currentRow--
                if (currentRow < 0) break
                currentCol = getText(currentRow).length
            }
        }

        return segments.toList()
    }

    private fun createInsertEdit(row: Int, col: Int, text: CharSequence): Insert {
        return EditFactory.createInsert(row, col, text, doc.lineSeparator().str())
    }

    private fun createDeleteEdit(row: Int, col: Int, text: CharSequence): Delete {
        return EditFactory.createDelete(row, col, text)
    }

    private fun createBackspaceEdit(row: Int, col: Int, text: String): Delete {
        return EditFactory.createBackspace(row, col, text, doc)
    }
}
