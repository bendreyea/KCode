package application

import io.mockk.*
import io.mockk.impl.annotations.RelaxedMockK
import org.editor.application.common.LineSeparator
import org.editor.application.doc.Document
import org.editor.application.doc.DocumentImpl
import org.editor.application.editor.EditorChangeListener
import org.editor.application.editor.EditorDocumentSnapshot
import org.editor.application.editor.TextEditImpl
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals


class TextEditImplSnapshotIndexTests {

    private lateinit var doc: Document
    private lateinit var textEdit: TextEditImpl

    @RelaxedMockK
    private lateinit var editorListener: EditorChangeListener

    @BeforeEach
    fun setUp() {
        doc = DocumentImpl.create(lineSeparator = LineSeparator.LF)
        textEdit = TextEditImpl(doc)
        textEdit.insert(0, 0, "Hello")
        editorListener = mockk(relaxed = true)
        textEdit.addEditorListener(editorListener)
    }

    @Test
    fun `snapshot index updates correctly after insert`() {
        // Set up a slot to capture the snapshot passed to the editor listener.
        val snapshotSlot = slot<EditorDocumentSnapshot>()
        every { editorListener.onEditorChange(any(), any(), capture(snapshotSlot)) } just Runs

        // Perform an insert operation at row 0, col 5, inserting " World".
        // This should update the text in row 0 from "Hello" to "Hello World".
        textEdit.insert(0, 5, " World")
        textEdit.insert(0, 11, "\n")

        // Verify that the editor listener was notified.
        verify { editorListener.onEditorChange(any(), any(), any()) }

        // Retrieve the captured snapshot.
        val snapshot = snapshotSlot.captured

        // The expected new text is "Hello World" (length 11).
        val newText = "Hello World"
        val baseOffset = snapshot.serial(0, 0)
        val endOffset = snapshot.serial(0, newText.length)

        // The difference should equal the length of the new text.
        assertEquals(newText.length, (endOffset - baseOffset).toInt(),
            "Snapshot index did not update the row length correctly")
    }
}
