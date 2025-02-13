package org.editor.application.editor

import org.editor.application.common.UserCaret
import org.editor.application.editor.Edit.*

/**
 * The sealed [Edit] class represents an editing action.
 *
 * It has three subtypes:
 * - [Insert]: An insertion of text.
 * - [Delete]: A deletion of text.
 * - [CompoundEdit]: A compound edit combining multiple [Insert] and [Delete] actions.
 */
sealed class Edit {
    abstract fun min(): UserCaret
    abstract fun max(): UserCaret
    abstract fun text(): String
    abstract fun flip(): Edit

    /**
     * Attempt to merge [other] into this edit.
     * Returns the merged edit or `null` if merging is not possible.
     */
    open fun merge(other: Edit): Edit? = null

    /**
     * Base class for concrete insert/delete edits.
     */
    abstract class ConcreteEdit : Edit() {
        abstract fun from(): UserCaret
        abstract fun to(): UserCaret
    }

    /**
     * An insertion edit.
     *
     * @property fromPos The starting caret.
     * @property toPos The caret position after insertion.
     * @property text The inserted text.
     */
    data class Insert(
        val fromPos: UserCaret,
        val toPos: UserCaret,
        private val text: String,
    ) : ConcreteEdit() {
        override fun min(): UserCaret = fromPos
        override fun max(): UserCaret = toPos
        override fun from(): UserCaret = fromPos
        override fun to(): UserCaret = toPos
        override fun text(): String = text

        override fun flip(): Edit = Delete(fromPos, toPos, text)
    }

    /**
     * A deletion edit.
     *
     * @property fromPos The starting caret.
     * @property toPos The caret position after deletion (optional).
     * @property text The deleted text.
     */
    data class Delete(
        val fromPos: UserCaret,
        val toPos: UserCaret? = null,
        private val text: String
    ) : ConcreteEdit() {

        private val actualToPos = toPos ?: fromPos
        private val minPos: UserCaret = minOf(fromPos, actualToPos)
        private val maxPos: UserCaret = maxOf(fromPos, actualToPos)

        constructor(fromPos: UserCaret, text: String) : this(fromPos, null, text)

        override fun min(): UserCaret = minPos
        override fun max(): UserCaret = maxPos
        override fun from(): UserCaret = fromPos
        override fun to(): UserCaret = toPos ?: fromPos
        override fun text(): String = text

        override fun flip(): Edit = Insert(minPos, maxPos, text)
    }

    /**
     * A compound edit representing a group of editing actions.
     *
     * @property edits The list of constituent edits.
     */
    data class CompoundEdit(
        val edits: List<ConcreteEdit>,
    ) : Edit() {

        override fun min(): UserCaret = edits.minOf { it.min() }
        override fun max(): UserCaret = edits.maxOf { it.max() }
        override fun text(): String = edits.joinToString("") { it.text() }

        override fun flip(): Edit {
            // Reverse the order and flip each constituent edit.
            val flipped = edits.map { it.flip() as ConcreteEdit }.reversed()
            return CompoundEdit(flipped)
        }
    }
}