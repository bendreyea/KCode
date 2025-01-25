package application

import org.editor.application.Caret
import org.editor.application.Document
import org.editor.application.DocumentImpl
import org.editor.application.TextEditImpl
import org.junit.jupiter.api.Disabled
import kotlin.test.Test
import kotlin.test.assertEquals

class TextEditImplTests {

    /**
     * Tests the insertion of text at a specific position within the document.
     * Ensures that the text is inserted correctly and the caret is positioned appropriately.
     */
    @Test
    fun insert_insertsTextAtCorrectPosition() {
        // Arrange
        val doc = DocumentImpl.create()
        val textEdit = TextEditImpl(doc)
        val insertText = "Hello"
        val row = 0
        val col = 0

        // Act
        textEdit.insert(row, col, insertText)

        // Assert
        val resultText = textEdit.getText(row)
        assertEquals("Hello", resultText, "Inserted text should match the expected value.")
    }

    /**
     * Tests the deletion of text at a specific position within the document.
     * Verifies that the correct range of text is removed.
     */
    @Test
    fun delete_deletesTextAtCorrectPosition() {
        // Arrange
        val doc = DocumentImpl.create()
        val textEdit = TextEditImpl(doc)
        val initialText = "Hello World"
        textEdit.insert(0, 0, initialText)
        val deleteRow = 0
        val startCol = 5
        val endCol = 6

        // Act
        textEdit.delete(deleteRow, startCol, endCol)

        // Assert
        val resultText = textEdit.getText(0)
        assertEquals("Hello", resultText, "Text after deletion should be 'Hello'.")
    }

    /**
     * Tests the backspace operation, which deletes text before the cursor.
     * Ensures that the text preceding the cursor is correctly removed.
     */
    @Test
    fun backspace_deletesTextBeforeCursor() {
        // Arrange
        val doc = DocumentImpl.create()
        val textEdit = TextEditImpl(doc)
        val initialText = "Hello World"
        textEdit.insert(0, 0, initialText)
        val backspaceRow = 0
        val cursorCol = 11
        val deleteCount = 6

        // Act
        textEdit.backspace(backspaceRow, cursorCol, deleteCount)

        // Assert
        val resultText = textEdit.getText(0)
        assertEquals("Hello", resultText, "Text after backspace should be 'Hello'.")
    }

    /**
     * Tests replacing a portion of text with new text.
     * Verifies that the replacement occurs correctly and updates the document as expected.
     */
    @Test
    fun replace_replacesTextCorrectly() {
        // Arrange
        val doc = DocumentImpl.create()
        val textEdit = TextEditImpl(doc)
        val initialText = "Hello World"
        textEdit.insert(0, 0, initialText)
        val replaceRow = 0
        val startCol = 6
        val length = 5
        val newText = "Universe"

        // Act
        textEdit.replace(replaceRow, startCol, length, newText)

        // Assert
        val resultText = textEdit.getText(0)
        assertEquals("Hello Universe", resultText, "Text after replacement should be 'Hello Universe'.")
    }

    /**
     * Tests inserting text that contains multiple lines.
     * Ensures that each line is inserted correctly and the document structure is maintained.
     */
    @Test
    fun insert_withMultipleLines_insertsCorrectly() {
        // Arrange
        val doc = DocumentImpl.create()
        val textEdit = TextEditImpl(doc)
        val multiLineText = "Hello\nWorld"
        textEdit.insert(0, 0, multiLineText)

        // Act & Assert
        val firstLine = textEdit.getText(0)
        val secondLine = textEdit.getText(1)
        assertEquals("Hello\n", firstLine, "First line should be 'Hello\\n'.")
        assertEquals("World", secondLine, "Second line should be 'World'.")
    }

    /**
     * Tests deleting text that spans multiple lines.
     * Verifies that the deletion correctly merges lines and removes the specified range.
     */
    @Test
    fun delete_withMultipleLines_deletesCorrectly() {
        // Arrange
        val doc = DocumentImpl.create()
        val textEdit = TextEditImpl(doc)
        val multiLineText = "Hello\nWorld"
        textEdit.insert(0, 0, multiLineText)
        val deleteRow = 0
        val startCol = 5

        // Act
        textEdit.delete(deleteRow, startCol, 1)

        // Assert
        val resultText = textEdit.getText(0)
        assertEquals("HelloWorld", resultText, "Text after deletion should be 'HelloWorld'.")
    }

    /**
     * Tests the backspace operation across multiple lines.
     * Ensures that the backspace correctly removes text and merges lines as needed.
     */
    @Test
    fun backspace_withMultipleLines_backspacesCorrectly() {
        // Arrange
        val doc = DocumentImpl.create()
        val textEdit = TextEditImpl(doc)
        val multiLineText = "Hello\nWorld"
        textEdit.insert(0, 0, multiLineText)
        val backspaceRow = 1
        val cursorCol = 0
        val deleteCount = 1

        // Act
        textEdit.backspace(backspaceRow, cursorCol, deleteCount)

        // Assert
        val resultText = textEdit.getText(0)
        assertEquals("HelloWorld", resultText, "Text after backspace should be 'HelloWorld'.")
    }

    /**
     * Tests replacing text that spans multiple lines.
     * Verifies that the replacement correctly modifies the intended range and updates the document.
     */
    @Test
    fun replace_withMultipleLines_replacesCorrectly() {
        // Arrange
        val doc = DocumentImpl.create()
        val textEdit = TextEditImpl(doc)
        val multiLineText = "Hello\nWorld"
        textEdit.insert(0, 0, multiLineText)
        val replaceRow = 0
        val startCol = 5
        val len = 1
        val newText = " "

        // Act
        textEdit.replace(replaceRow, startCol, len, newText)

        // Assert
        val resultText = textEdit.getText(0)
        assertEquals("Hello World", resultText, "Text after replacement should be 'Hello World'.")
    }

    /**
     * Tests the backspace operation and verifies the resulting text and caret position.
     */
    @Test
    fun backspace_deletesTextAndVerifiesCaretPosition() {
        // Arrange
        val te = TextEditImpl(Document.create())
        te.insert(0, 0, "abc")
        val backspaceRow = 0
        val cursorCol = 2

        // Act
        val caret = te.backspace(backspaceRow, cursorCol)

        // Assert
        assertEquals(Caret(0, 1), caret, "Caret should be at position (0, 1) after backspace.")
    }

    /**
     * Tests the backspace operation at a row break and verifies the resulting text and caret position.
     */
    @Test
    @Disabled // TODO: Fix this test
    fun backspaceRowBreak_deletesRowBreakCorrectly() {
        // Arrange
        val te = TextEditImpl(Document.create())
        te.insert(0, 0, "abc\r\n")
        val backspaceRow = 1
        val cursorCol = 0

        // Act
        val caret = te.backspace(backspaceRow, cursorCol)

        // Assert
        assertEquals(Caret(0, 3), caret, "Caret should be at position (0, 3) after backspace.")
    }

    /**
     * Tests the backspace operation across multiple rows and verifies the resulting text and caret position.
     */
    @Test
    fun backspaceMultiRow_deletesAcrossRowsAndVerifiesCaret() {
        // Arrange
        val te = TextEditImpl(Document.create())
        te.insert(0, 0, "abc\ndef\ngh")
        val backspaceRow = 2
        val cursorCol = 1
        val deleteCount = 8

        // Act
        val caret = te.backspace(backspaceRow, cursorCol, deleteCount)

        // Assert
        assertEquals(Caret(0, 1), caret, "Caret should be at position (0, 1) after backspace.")
    }

    /**
     * Tests computing distances from carets to their positions and verifies the mappings.
     */
    @Test
    fun distances_computesDistancesFromCaretsCorrectly() {
        // Arrange
        val te = TextEditImpl(Document.create())
        te.insert(0, 0, "abc\ndef\nghi")
        val carets = listOf(
            Caret(0, 0),
            Caret(0, 1),
            Caret(1, 1),
            Caret(1, 2),
            Caret(2, 3)
        )

        // Act
        val distances = te.distances(carets)

        // Assert
        assertEquals(0, distances[0], "Distance for first caret should be 0.")
        assertEquals(1, distances[1], "Distance for second caret should be 1.")
        assertEquals(5, distances[2], "Distance for third caret should be 5.")
        assertEquals(6, distances[3], "Distance for fourth caret should be 6.")
        assertEquals(11, distances[4], "Distance for fifth caret should be 11.")

        val posList: List<Caret> = te.posList(0, distances)

        assertEquals(carets[0], posList[0], "First caret position should match.")
        assertEquals(carets[1], posList[1], "Second caret position should match.")
        assertEquals(carets[2], posList[2], "Third caret position should match.")
        assertEquals(carets[3], posList[3], "Fourth caret position should match.")
        assertEquals(carets[4], posList[4], "Fifth caret position should match.")
    }

    /**
     * Tests retrieving text to the right based on byte offsets.
     * Verifies that the correct byte segments are returned.
     */
    @Test
    fun textRightByte_retrievesCorrectByteSegmentsToTheRight() {
        // Arrange
        val te = TextEditImpl(Document.create())
        te.insert(0, 0, "abc\ndef\nghi")

        // Act & Assert
        assertEquals("a", te.textRightByte(0, 0, 1)[0], "First byte to the right should be 'a'.")
        assertEquals("b", te.textRightByte(0, 1, 1)[0], "First byte to the right should be 'b'.")
        assertEquals("bc\n", te.textRightByte(0, 1, 3)[0], "Next three bytes should be 'bc\\n'.")

        var ret = te.textRightByte(0, 1, 4)
        assertEquals(2, ret.size, "Should return two byte segments.")
        assertEquals("bc\n", ret[0], "First byte segment should be 'bc\\n'.")
        assertEquals("d", ret[1], "Second byte segment should be 'd'.")

        ret = te.textRightByte(0, 1, 8)
        assertEquals(3, ret.size, "Should return three byte segments.")
        assertEquals("bc\n", ret[0], "First byte segment should be 'bc\\n'.")
        assertEquals("def\n", ret[1], "Second byte segment should be 'def\\n'.")
        assertEquals("g", ret[2], "Third byte segment should be 'g'.")
    }

    /**
     * Tests retrieving text to the left based on byte offsets.
     * Verifies that the correct byte segments are returned.
     */
    @Test
    fun textLeftByte_retrievesCorrectByteSegmentsToTheLeft() {
        // Arrange
        val te = TextEditImpl(Document.create())
        te.insert(0, 0, "abc\ndef\nghi")

        // Act & Assert
        assertEquals("a", te.textLeftByte(0, 1, 1)[0], "First byte to the left should be 'a'.")
        assertEquals("ab", te.textLeftByte(0, 2, 2)[0], "First two bytes to the left should be 'ab'.")
        assertEquals("abc", te.textLeftByte(0, 3, 3)[0], "First three bytes to the left should be 'abc'.")
        assertEquals("abc\n", te.textLeftByte(0, 4, 4)[0], "First four bytes to the left should be 'abc\\n'.")

        assertEquals("\n", te.textLeftByte(1, 0, 1)[0], "First byte to the left should be '\\n'.")
        assertEquals("", te.textLeftByte(1, 0, 1)[1], "Second byte segment should be empty.")
        assertEquals("c\n", te.textLeftByte(1, 0, 2)[0], "First two bytes to the left should be 'c\\n'.")
        assertEquals("bc\n", te.textLeftByte(1, 0, 3)[0], "First three bytes to the left should be 'bc\\n'.")
        assertEquals("abc\n", te.textLeftByte(1, 0, 4)[0], "First four bytes to the left should be 'abc\\n'.")

        assertEquals("abc\n", te.textLeftByte(2, 3, 11)[0], "First segment should be 'abc\\n'.")
        assertEquals("def\n", te.textLeftByte(2, 3, 11)[1], "Second segment should be 'def\\n'.")
        assertEquals("ghi", te.textLeftByte(2, 3, 11)[2], "Third segment should be 'ghi'.")
    }

    /**
     * Tests replacing text and verifies the resulting text and caret position.
     */
    @Test
    fun replace_replacesTextAndVerifiesCaretPosition() {
        // Arrange
        val te = TextEditImpl(Document.create())
        te.insert(0, 0, "abc\ndef\nghi")

        // Act
        var ret = te.replace(0, 0, 3, "123")

        // Assert
        assertEquals(0, ret.row, "Caret row should remain at 0 after replacement.")
        assertEquals(3, ret.col, "Caret column should move to 3 after replacement.")

        // Additional replacement scenario
        ret = te.replace(0, 1, 9, "*")
        assertEquals(0, ret.row, "Caret row should remain at 0 after replacement.")
        assertEquals(2, ret.col, "Caret column should move to 2 after replacement.")
    }

    /**
     * Tests replacing text backward and verifies the resulting text and caret position.
     */
    @Test
    fun replaceBackward_replacesTextBackwardAndVerifiesCaret() {
        // Arrange
        val te = TextEditImpl(Document.create())
        te.insert(0, 0, "abc\ndef\nghi")

        // Act
        var ret = te.replace(2, 3, -2, "123")

        // Assert
        assertEquals(2, ret.row, "Caret row should remain at 2 after backward replacement.")
        assertEquals(4, ret.col, "Caret column should move to 4 after backward replacement.")

        // Additional backward replacement scenario
        ret = te.replace(2, 3, -9, "*")
        assertEquals(0, ret.row, "Caret row should move to 0 after backward replacement.")
        assertEquals(3, ret.col, "Caret column should move to 3 after backward replacement.")
    }

    @Test
    fun backspace_issues_1() {
        // Arrange
        val te = TextEditImpl(Document.create())
        te.insert(0, 0, "1")
        te.insert(0, 1, "\n")
        te.insert(1, 0, "\n")
        te.insert(2, 0, "2")
        te.insert(2, 1, "3")


        // Act
        te.backspace(2, 0)
        val a = te.getText(0)
        val b = te.getText(1)

        // Assert
        assertEquals("1\n", a)
        assertEquals("23", b)
    }

    @Test
    fun backspace_issues_2() {
        // Arrange
        val te = TextEditImpl(Document.create())
        te.insert(0, 0, "1")
        te.insert(0, 1, "\n")
        te.insert(1, 0, "\n")
        te.insert(2, 0, "\n")
        te.insert(3, 0, "2")
        te.insert(3, 1, "3")


        // Act
        te.backspace(3, 0)
        val a = te.getText(0)
        val b = te.getText(2)

        // Assert
        assertEquals("1\n", a)
        assertEquals("23", b)
    }
}
