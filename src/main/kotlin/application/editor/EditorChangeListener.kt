package org.editor.application.editor

interface EditorChangeListener {
    fun onEditorChange(start: Int, end: Int, editorSnapshot: EditorDocumentSnapshot)
}