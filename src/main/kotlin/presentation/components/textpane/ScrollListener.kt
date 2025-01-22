package org.editor.presentation.components.textpane

import org.editor.application.Caret

/**
 * Interface for listening to caret movement events.
 */
interface CaretListener {
    /**
     * Called when the caret has moved.
     *
     * @param newCaret The new position of the caret.
     */
    fun caretMoved(newCaret: Caret)
}