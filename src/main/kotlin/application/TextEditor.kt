package org.editor.application

import org.editor.application.Edit.*
import java.nio.charset.Charset
import java.util.*
import kotlin.math.min


/**
 * A concrete implementation of [TextEdit] which maintains a queue of edits,
 * performs them on the underlying [Document], and supports undo/redo operations.
 *
 * @property doc The underlying document that stores text data.
 */
class TextEditImpl(
    private val doc: Document
) : TextEdit {

    // region -- Queues & Buffers ----------------------------------------------------

    /**
     * Queue for edits that have not yet been flushed to the [doc].
     * These are edits we keep "in memory" until we decide to permanently apply them
     * (e.g., on a keyUp event or after a certain threshold).
     */
    private val editQueue = ArrayDeque<Edit>()

    /**
     * Stack for undoable edits.
     * When we do a `flush()`, we move these edits to [undo] for potential undo operations.
     */
    private val undo = ArrayDeque<Edit>()

    /**
     * Stack for edits.
     * If an undo operation is performed, we push the undone edits here
     * so that we can perform a redo if needed.
     */
    private val redo = ArrayDeque<Edit>()

    /**
     * A "dry run" buffer that holds the intermediate state of rows modified
     * by unflushed edits. This avoids constantly modifying the [doc] directly
     * while the user is typing.
     */
    private val dryBuffer = mutableMapOf<Int, String>()

    // endregion

    // region -- Insert --------------------------------------------------------------

    /**
     * Inserts [text] at position ([row], [col]).
     * @return The new cursor position after insertion.
     */
    override fun insert(row: Int, col: Int, text: String): Caret {
        if (text.isEmpty())
            return Caret(row, col)

        val edit = createInsertEdit(row, col, text)
        pushEdit(edit)
        return edit.to()
    }

    // endregion

    // region -- Delete --------------------------------------------------------------

    /**
     * Deletes a single character at ([row], [col]).
     * @return The deleted character(s) as a String.
     */
    override fun delete(row: Int, col: Int): Caret {
        return deleteChar(row, col, 1)
    }

    /**
     * Deletes [len] characters at ([row], [col]) moving to the right.
     * @return The deleted substring.
     */
    override fun delete(row: Int, col: Int, len: Int): Caret {
        val toDelete = joinStrings(textRightByte(row, col, len))
        val edit = createDeleteEdit(row, col, toDelete)
        pushEdit(edit)
        return edit.min()
    }

    /**
     * Internal helper to delete [chCount] characters from ([row], [col]).
     */
    private fun deleteChar(row: Int, col: Int, chCount: Int): Caret {
        val toDelete = joinStrings(textRight(row, col, chCount))
        val edit = createDeleteEdit(row, col, toDelete)
        pushEdit(edit)
        return edit.min()
    }

    // endregion

    // region -- Backspace -----------------------------------------------------------

    /**
     * Backspace a single character at ([row], [col]).
     * @return The new cursor position after backspacing.
     */
    override fun backspace(row: Int, col: Int): Caret {
        return backspaceChar(row, col, 1)
    }

    /**
     * Backspace [len] characters at ([row], [col]), moving to the left.
     */
    override fun backspace(row: Int, col: Int, len: Int): Caret {
        val toDelete = joinStrings(textLeftByte(row, col, len))
        val edit = createBackspaceEdit(row, col, toDelete)
        pushEdit(edit)
        return edit.min()
    }

    /**
     * Internal helper to backspace [chCount] characters at ([row], [col]).
     */
    private fun backspaceChar(row: Int, col: Int, chCount: Int): Caret {
        val toDelete = joinStrings(textLeft(row, col, chCount))
        val edit = createBackspaceEdit(row, col, toDelete)
        pushEdit(edit)
        return edit.min()  // so single-line backspaces move left by chCount
    }

    // endregion

    // region -- Replace -------------------------------------------------------------

    /**
     * Replaces [len] characters at ([row], [col]) with [text].
     * If [len] == 0, it is effectively an insert.
     * If [len] > 0, it is a delete-then-insert.
     * If [len] < 0, it is a backspace-then-insert.
     */
    override fun replace(row: Int, col: Int, len: Int, text: String): Caret {
        if (len == 0) {
            // Just an insert
            return insert(row, col, text)
        }

        return if (len > 0) {
            // Delete then insert
            val delText = joinStrings(textRightByte(row, col, len))
            val del = createDeleteEdit(row, col, delText)
            val ins = createInsertEdit(row, col, text)
            val compound = Edit.Cmp(listOf(del, ins))
            pushEdit(compound)
            ins.to()
        } else {
            // Backspace then insert
            // For negative len, backspace + then insert:
            val absLen = -len
            val delText = joinStrings(textLeftByte(row, col, absLen))
            val bs = createBackspaceEdit(row, col, delText)

            // Insert exactly where the backspace finished -> bs.min()
            val ins = createInsertEdit(bs.min().row, bs.min().col, text)

            val compound = Edit.Cmp(listOf(bs, ins))
            pushEdit(compound)

            // Return ins.to(), which should be (bs.min().row, bs.min().col + text.length)
            ins.to()
        }
    }

    // endregion

    // region -- Undo / Redo ---------------------------------------------------------

    /**
     * Undoes the last applied edit. Returns the cursor position(s) associated
     * with the undone edit(s).
     */
    private fun undo(): List<Caret> {
        flush()
        val edit = undoLast()
        return when (edit) {
            is Edit.ConcreteEdit -> listOf(edit.to())
            is Edit.Cmp -> listOf(edit.edits().last().to())
            else -> emptyList()
        }
    }

    /**
     * Redoes the last undone edit. Returns the cursor position(s) associated
     * with the redone edit(s).
     */
    private fun redo(): List<Caret> {
        flush()
        val edit = redoLast()
        return when (edit) {
            is Edit.ConcreteEdit -> listOf(edit.to())
            is Edit.Cmp -> listOf(edit.edits().last().to())
            else -> emptyList()
        }
    }

    /**
     * Checks if there is at least one undo record in the queue or stack.
     */
    private fun hasUndoRecord(): Boolean {
        return editQueue.isNotEmpty() || undo.isNotEmpty()
    }

    // endregion

    // region -- Text Access ---------------------------------------------------------

    /**
     * Gets the text at [row]. If there are unflushed edits that modify [row],
     * returns the in-memory buffer instead of the original text from [doc].
     */
    override fun getText(row: Int): String {
        // If we have unflushed edits, do a dry run to populate [dryBuffer] if empty
        if (editQueue.isNotEmpty() && dryBuffer.isEmpty()) {
            applyEditsInMemory()
        }
        return dryBuffer[row] ?: getDocText(row)
    }

    private fun getDocText(row: Int): String {
        if (doc.rawSize() == 0L)
            return ""

        return doc.getText(row).toString()
    }

    /**
     * Gets text between cursor positions [start] and [end]. If [end] < [start], swap them.
     */
    override fun getText(start: Caret, end: Caret): String {
        var (sRow, sCol) = start
        var (eRow, eCol) = end

        // Swap if needed
        if (end < start) {
            sRow = end.row
            sCol = end.col
            eRow = start.row
            eCol = start.col
        }

        val sb = StringBuilder()
        for (i in sRow..eRow) {
            var rowText = getText(i)
            // Trim to eCol if we are on the final row
            if (i == eRow) {
                rowText = rowText.substring(0, min(eCol, rowText.length))
            }
            // Trim from sCol if we are on the initial row
            if (i == sRow) {
                rowText = rowText.substring(min(sCol, rowText.length))
            }
            sb.append(rowText)
        }
        return sb.toString()
    }

    // endregion

    // region -- Flush & Clear -------------------------------------------------------

    /**
     * Permanently applies all pending edits to [doc], clears [editQueue],
     * and populates [undo] with flipped operations (for future undo).
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
     * Flushes all pending edits and clears undo/redo stacks.
     */
    private fun clear() {
        flush()
        undo.clear()
        redo.clear()
    }

    // endregion

    // region -- Document Info & Save -----------------------------------------------

    override fun rows(): Int = doc.rows()

    override fun charset(): Charset = doc.charset()

    // endregion

    // region -- Edit Creation (Insert / Delete / Backspace) -------------------------

    /**
     * Creates an [Edit.Insert] object, adjusting the ending row/col if the text spans multiple lines.
     */
    private fun createInsertEdit(row: Int, col: Int, text: String): Edit.Insert {
        val indexOfNewline = text.lastIndexOf('\n')
        return if (indexOfNewline < 0) {
            // The inserted text has no line break
            Edit.Insert(
                Caret(row, col),
                Caret(row, col + text.length),
                text,
            )
        } else {
            // The inserted text spans multiple rows
            val additionalRows = text.count { it == '\n' }
            val newRow = row + additionalRows
            val newCol = text.substring(indexOfNewline + 1).length
            Edit.Insert(
                Caret(row, col),
                Caret(newRow, newCol),
                text,
            )
        }
    }

    /**
     * Creates an [Edit.Delete] object that deletes [text] from ([row], [col]).
     */
    private fun createDeleteEdit(row: Int, col: Int, text: String): Edit.Delete {
        return Edit.Delete(Caret(row, col), text)
    }

    /**
     * Creates a backspace [Edit.Delete] object that removes [text] behind ([row], [col]),
     * adjusting the new cursor position as needed.
     */
    private fun createBackspaceEdit(row: Int, col: Int, text: String): Edit.Delete {
        // Suppose text = "\n" if we’re removing a newline in the previous row
        val newRow = row - countRowBreak(text)
        val newCol = if (row == newRow) {
            // Single-line backspace: shift col left by text.length
            col - text.length
        } else {
            // Multi-line backspace: removing a newline from a previous row
            // 'index' is first newline in text
            val index = text.indexOf('\n')
            val startOffset = if (index >= 0) index + 1 else text.length
            (getText(newRow).length - startOffset).coerceAtLeast(0)
        }

        // The *start* of deletion is (newRow,newCol); the *end* is the current (row,col)
        return Edit.Delete(
            Caret(newRow, newCol),
            Caret(row, col),
            text
        )
    }

    // endregion

    // region -- Push & Merge Edits -------------------------------------------------

    /**
     * Pushes an [edit] into the queue, attempting to merge with the last queued edit.
     * If merging is not possible, we flush existing edits and push the new one.
     */
    private fun pushEdit(edit: Edit) {
        val lastEdit = editQueue.peekLast()
        val merged = lastEdit?.merge(edit)

        if (merged != null) {
            // If merge is successful, flush the queue so there's no partial state
            // then push the merged edit as a single operation
            editQueue.removeLast()
            flush()
            editQueue.push(merged)
            return
        }

        // If we cannot merge, flush everything before pushing the new edit
        flush()
        editQueue.push(edit)

        // If the edit spans multiple lines, flush immediately (avoid large multi-row buffering)
        when (edit) {
            is Edit.ConcreteEdit -> {
                if (edit.text().contains("\n")) {
                    flush()
                }
            }
            is Edit.Cmp -> {
                if (edit.edits().any { it.text().contains("\n") }) {
                    flush()
                }
            }
        }
    }

    // endregion

    // region -- Undo / Redo Internal Helpers ----------------------------------------

    /**
     * Applies an undo operation by popping from [undo], applying it to [doc],
     * and pushing the flipped edit to [redo].
     */
    private fun undoLast(): Edit? {
        if (undo.isEmpty()) return null
        val edit = undo.pop()
        applyToDocument(edit)
        redo.push(edit.flip())
        return edit
    }

    /**
     * Applies a redo operation by popping from [redo], applying it to [doc],
     * and pushing the flipped edit to [undo].
     */
    private fun redoLast(): Edit? {
        if (redo.isEmpty()) return null
        val edit = redo.pop()
        applyToDocument(edit)
        undo.push(edit.flip())
        return edit
    }

    // endregion

    // region -- Apply Edits to Document / Dry Buffer --------------------------------

    /**
     * Applies the [edit] directly to [doc], handling insert/delete logic.
     * Compound edits are applied in sequence.
     */
    private fun applyToDocument(edit: Edit) {
        when (edit) {
            is Edit.Insert -> doc.insert(edit.min().row, edit.min().col, edit.text())
            is Edit.Delete -> {
                println("Deleting '${edit.text()}' at row=${edit.min().row}, col=${edit.min().col}")
                doc.delete(edit.min().row, edit.min().col, edit.text())
            }
            is Edit.Cmp -> edit.edits().forEach { applyToDocument(it) }
            is Edit.ConcreteEdit -> {
                // If you have more concrete subtypes in the future, handle them here
                // For now, Ins and Del are covered above, so no-op or error
            }
        }
    }

    /**
     * Applies all edits in the [editQueue] to [dryBuffer], simulating changes in memory.
     * Does not modify [doc].
     */
    private fun applyEditsInMemory() {
        dryBuffer.clear()
        editQueue.forEach { applyEditToDryBuffer(it) }
    }

    /**
     * Recursively applies an [edit] to [dryBuffer].
     */
    private fun applyEditToDryBuffer(edit: Edit) {
        when (edit) {
            is Edit.Insert -> {
                val original = dryBuffer[edit.from().row] ?: doc.getText(edit.from().row).toString()
                val newText = original.substring(0, edit.min().col) +
                        edit.text() +
                        original.substring(edit.min().col.coerceAtMost(original.length))
                dryBuffer[edit.from().row] = newText
            }
            is Edit.Delete -> {
                val original = dryBuffer[edit.from().row] ?: doc.getText(edit.from().row).toString()
                val newText = original.substring(0, edit.min().col) +
                        original.substring((edit.min().col + edit.text().length).coerceAtMost(original.length))
                dryBuffer[edit.from().row] = newText
            }
            is Edit.Cmp -> edit.edits().forEach { applyEditToDryBuffer(it) }
            is Edit.ConcreteEdit -> {
                // Additional subtypes if any
            }
        }
    }

    // endregion

    // region -- Text Extraction Helpers (Right / Left) ------------------------------

    /**
     * Returns up to [chLen] characters to the right of ([row], [col]) across lines, if needed.
     * This is character-based, handling Unicode with [Texts.chLength].
     */
    fun textRight(row: Int, col: Int, chLen: Int): List<String> {
        var remainder = chLen
        var currentCol = col
        val ret = mutableListOf<String>()

        for (i in row until doc.rows()) {
            val line = getText(i)
            if (currentCol >= line.length) break
            val text = line.substring(currentCol)
            if (text.isEmpty()) break

            val len = Texts.chLength(text)
            if (remainder - len <= 0) {
                ret += Texts.left(text, remainder)
                break
            }
            ret += text
            remainder -= len
            currentCol = 0
        }

        // If the last collected text ends with a newline, append an empty string
        if (ret.isNotEmpty() && ret.last().endsWith("\n")) {
            ret += ""
        }
        return ret
    }

    /**
     * Returns up to [chLen] characters to the left of ([row], [col]) across lines, if needed.
     */
    fun textLeft(row: Int, col: Int, chLen: Int): List<String> {
        var remainder = chLen
        val ret = LinkedList<String>()
        var r = row
        var c = col

        while (r >= 0 && remainder > 0) {
            val line = getText(r)
            val text = if (r == row) {
                line.substring(0, c.coerceAtMost(line.length))
            } else {
                line
            }

            val len = Texts.chLength(text)
            if (remainder - len <= 0) {
                // The needed substring is within this line
                ret.addFirst(Texts.right(text, remainder))
                break
            }

            // Otherwise, add the whole line and handle the newline
            ret.addFirst(text)
            remainder -= len
            r--

            if (r >= 0) {
                // Add the newline character from the previous line
                ret.addFirst("\n")
                remainder -= 1
            }
        }
        return ret
    }


    /**
     * Byte-based version of [textRight].
     */
    fun textRightByte(row: Int, col: Int, byteLen: Int): List<String> {
        var remainder = byteLen
        var currentCol = col
        val ret = mutableListOf<String>()

        for (i in row until doc.rows()) {
            val line = getText(i)
            if (currentCol >= line.length)
                break

            val text = line.substring(currentCol)
            if (text.isEmpty())
                break

            val len = text.length
            if (remainder - len <= 0) {
                ret += text.substring(0, remainder)
                break
            }
            ret += text
            remainder -= len
            currentCol = 0
        }

        return ret
    }

    /**
     * Byte-based version of [textLeft].
     */
    fun textLeftByte(row: Int, col: Int, byteLen: Int): List<String> {
        var remainder = byteLen
        val ret = LinkedList<String>()
        var r = row
        var c = col

        // If we're at the start of the line, move to the previous line
        if (c == 0) {
            ret.add("")
            r--
        }

        while (r >= 0) {
            val line = getText(r)
            val text = if (c > 0) {
                line.substring(0, c.coerceAtMost(line.length))
            } else {
                line
            }
            val len = text.length
            if (remainder - len <= 0) {
                ret.addFirst(text.substring((len - remainder).coerceAtLeast(0)))
                break
            }
            ret.addFirst(text)
            remainder -= len
            r--
            c = 0
        }

        return ret
    }

    // endregion

    // region -- Distances & Positions -----------------------------------------------

    /**
     * For a list of [poss], returns an IntArray of "distances" from the start of the document,
     * counting columns in each row consecutively.
     */
    fun distances(poss: List<Caret>): IntArray {
        if (poss.isEmpty()) return IntArray(0)

        val ret = IntArray(poss.size)
        var distance = 0
        var index = 0
        var pos = poss.first()
        val firstRow = poss.first().row
        val lastRow = poss.last().row

        for (i in firstRow..lastRow) {
            val rowText = getText(i)
            while (pos.row == i) {
                ret[index++] = distance + pos.col
                if (index >= poss.size) break
                pos = poss[index]
            }
            distance += rowText.length
            if (index >= poss.size) break
        }
        return ret
    }

    /**
     * Converts an array of [distances] back into [Caret] positions starting at [row].
     */
    fun posList(row: Int, distances: IntArray): List<Caret> {
        val poss = mutableListOf<Caret>()
        var total = 0
        var index = 0
        var r = row

        while (index < distances.size) {
            val rowText = getText(r)
            if (rowText.isEmpty()) break
            while (total + rowText.length >= distances[index]) {
                poss += Caret(r, distances[index] - total)
                index++
                if (index >= distances.size) break
            }
            total += rowText.length
            r++
        }
        return poss
    }

    // endregion

    // region -- Utilities & Accessors -----------------------------------------------

    fun getDoc(): Document = doc
    fun getDryBuffer(): Map<Int, String> = dryBuffer
    fun getEditQueue(): Deque<Edit> = editQueue
    fun getUndoStack(): Deque<Edit> = undo
    fun getRedoStack(): Deque<Edit> = redo

    /**
     * Joins a list of strings without any delimiter.
     */
    private fun joinStrings(list: List<String>): String = buildString {
        for (str in list) append(str)
    }

    /**
     * Counts the number of newline characters in [text].
     */
    private fun countRowBreak(text: String): Int = text.count { it == '\n' }

    // endregion
}


/**
 * The sealed [Edit] class represents an editing action:
 * - [Insert]: Insert text
 * - [Delete]: Delete text
 * - [Cmp]: Compound edit combining multiple [Insert]/[Delete]
 * - [ConcreteEdit]: Abstract parent for [Insert] and [Delete]
 */
sealed class Edit {
    abstract fun min(): Caret
    abstract fun max(): Caret
    abstract fun text(): String
    abstract fun flip(): Edit

    /**
     * Attempt to merge [other] into this edit, returning the merged edit or `null` if merge is impossible.
     */
    open fun merge(other: Edit): Edit? = null

    /**
     * A base class for inserts and deletes that share `from()` and `to()` cursors.
     */
    abstract class ConcreteEdit : Edit() {
        abstract fun from(): Caret
        abstract fun to(): Caret
    }

    /**
     * An insertion edit from [fromPos] to [toPos] with inserted [txt].
     */
    class Insert(
        private val fromPos: Caret,
        private val toPos: Caret,
        private val txt: String,
    ) : ConcreteEdit() {
        override fun min(): Caret = fromPos
        override fun max(): Caret = toPos
        override fun from(): Caret = fromPos
        override fun to(): Caret = toPos
        override fun text(): String = txt
        override fun flip(): Edit {
            // Flip insertion => deletion
            return Delete(fromPos, toPos, txt)
        }
    }

    /**
     * A deletion edit from [fromPos] and optionally [toPos], removing [txt].
     */
    class Delete(
        private val fromPos: Caret,
        private val toPos: Caret? = null,
        private val txt: String
    ) : Edit.ConcreteEdit() {

        private val minPos: Caret
        private val maxPos: Caret

        constructor(fromPos: Caret, txt: String)
                : this(fromPos, null, txt)

        init {
            val actualToPos = toPos ?: fromPos

            // Compare fromPos and toPos, then assign appropriately
            if (fromPos <= actualToPos) {
                minPos = fromPos
                maxPos = actualToPos
            } else {
                minPos = actualToPos
                maxPos = fromPos
            }
        }

        override fun min(): Caret = minPos
        override fun max(): Caret = maxPos
        override fun from(): Caret = fromPos
        override fun to(): Caret = toPos ?: fromPos
        override fun text(): String = txt

        override fun flip(): Edit {
            // Flip deletion => insertion
            return Insert(minPos, maxPos, txt)
        }
    }

    /**
     * A compound edit consisting of multiple [ConcreteEdit] operations.
     * Useful for grouped actions like "delete then insert" or "bulk insertion in multiple places."
     */
    class Cmp(
        private val list: List<ConcreteEdit>,
    ) : Edit() {
        fun edits(): List<ConcreteEdit> = list
        override fun min(): Caret = list.minByOrNull { it.min() }?.min() ?: Caret(0, 0)
        override fun max(): Caret = list.maxByOrNull { it.max() }?.max() ?: Caret(0, 0)
        override fun text(): String = list.joinToString("") { it.text() }

        override fun flip(): Edit {
            // Reverse and flip each child edit
            val reversed = list.map { it.flip() as ConcreteEdit }.reversed()
            return Cmp(reversed)
        }
    }
}


/**
 * text utility object for Unicode or other specialized operations.
 */
object Texts {
    fun chLength(text: String): Int = text.length
    fun left(text: String, count: Int): String = text.take(count.coerceAtMost(text.length))
    fun right(text: String, count: Int): String = text.takeLast(count.coerceAtMost(text.length))
}

