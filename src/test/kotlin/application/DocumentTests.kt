package application

import org.editor.application.DocumentImpl
import org.editor.application.NewLine
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.test.Test

class DocumentTests {

    /**
     * Tests inserting text into the document.
     * Ensures that the text is inserted correctly and can be retrieved accurately.
     */
    @Test
    fun insert_insertsTextCorrectly() {
        // Arrange
        val document = DocumentImpl.create()
        val insertText = "Hello"
        val row = 0
        val col = 0

        // Act
        document.insert(row, col, insertText)

        // Assert
        val resultText = document.getText(row)
        assertEquals("Hello", resultText, "Inserted text should match the expected value.")
    }

    /**
     * Tests deleting text from a specific position within the document.
     * Verifies that the correct range of text is removed.
     */
    @Test
    fun delete_deletesTextCorrectly() {
        // Arrange
        val document = DocumentImpl.create()
        val initialText = "Hello World"
        document.insert(0, 0, initialText)
        val deleteRow = 0
        val deleteColStart = 5
        val deleteColEnd = 11
        val textToDelete = " World"

        // Act
        document.delete(deleteRow, deleteColStart, textToDelete)

        // Assert
        val resultText = document.getText(0)
        assertEquals("Hello", resultText, "Text after deletion should be 'Hello'.")
    }


    /**
     * Tests retrieving text from a valid row.
     * Ensures that the correct text is returned.
     */
    @Test
    fun getText_withValidRow_returnsCorrectText() {
        // Arrange
        val document = DocumentImpl.create()
        val insertText = "Hello"
        val row = 0
        document.insert(row, 0, insertText)

        // Act
        val retrievedText = document.getText(row)

        // Assert
        assertEquals("Hello", retrievedText, "Retrieved text should match the inserted text.")
    }

    /**
     * Tests retrieving the number of rows in the document.
     * Verifies that the row count reflects the inserted content accurately.
     */
    @Test
    fun rows_returnsCorrectNumberOfRows() {
        // Arrange
        val document = DocumentImpl.create()
        val insertText = "Hello\nWorld"
        document.insert(0, 0, insertText)

        // Act
        val rowCount = document.rows()
        val firstRowText = document.getText(0)
        val secondRowText = document.getText(1)

        // Assert
        assertEquals(2, rowCount, "Document should contain 2 rows.")
        assertEquals("Hello\n", firstRowText, "First row should be 'Hello\\n'.")
        assertEquals("World", secondRowText, "Second row should be 'World'.")
    }

    /**
     * Tests retrieving the raw size of the document.
     * Ensures that the size matches the total number of characters inserted.
     */
    @Test
    fun rawSize_returnsCorrectSize() {
        // Arrange
        val document = DocumentImpl.create()
        val insertText = "Hello"
        document.insert(0, 0, insertText)

        // Act
        val size = document.rawSize()

        // Assert
        assertEquals(5, size, "Raw size should be 5 for the inserted text 'Hello'.")
    }

    /**
     * Tests retrieving the serial number for a given row and column.
     * Ensures that the serial corresponds to the correct position.
     */
    @Test
    fun serial_returnsCorrectSerial() {
        // Arrange
        val document = DocumentImpl.create()
        val insertText = "Hello"
        document.insert(0, 0, insertText)
        val row = 0
        val col = 0

        // Act
        val serialNumber = document.serial(row, col)

        // Assert
        assertEquals(0, serialNumber, "Serial number for (0,0) should be 0.")
    }

    /**
     * Tests retrieving the cursor position from a given serial number.
     * Verifies that the correct row and column are returned.
     */
    @Test
    fun pos_returnsCorrectCursor() {
        // Arrange
        val document = DocumentImpl.create()
        val insertText = "Hello"
        document.insert(0, 0, insertText)
        val serialNumber = 0L

        // Act
        val cursor = document.pos(serialNumber)

        // Assert
        assertEquals(0, cursor.row, "Cursor row should be 0 for serial 0.")
        assertEquals(0, cursor.col, "Cursor column should be 0 for serial 0.")
    }

    /**
     * Tests retrieving the charset of the document.
     * Ensures that the default charset is UTF-8.
     */
    @Test
    fun charset_returnsCorrectCharset() {
        // Arrange
        val document = DocumentImpl.create()

        // Act
        val charset = document.charset()

        // Assert
        assertEquals(Charset.forName("UTF-8"), charset, "Document charset should be UTF-8.")
    }

    /**
     * Tests retrieving the row ending type of the document.
     * Verifies that it matches the platform-specific newline.
     */
    @Test
    fun rowEnding_returnsCorrectNewLine() {
        // Arrange
        val document = DocumentImpl.create()

        // Act
        val rowEnding = document.rowEnding()

        // Assert
        assertEquals(NewLine.platform, rowEnding, "Row ending should match the platform-specific newline.")
    }

    /**
     * Tests retrieving the Byte Order Mark (BOM) of the document.
     * Ensures that the BOM is empty by default.
     */
    @Test
    fun bom_returnsCorrectBom() {
        // Arrange
        val document = DocumentImpl.create()

        // Act
        val bom = document.bom()

        // Assert
        assertArrayEquals(ByteArray(0), bom, "BOM should be empty by default.")
    }

    /**
     * Tests inserting text into multiple lines and verifies the document structure.
     * Ensures that each line is correctly separated and stored.
     */
    @Test
    fun insert_withMultipleLines_insertsCorrectly() {
        // Arrange
        val document = DocumentImpl.create()
        val insertText = "Hello\nWorld"
        val row = 0
        val col = 0

        // Act
        document.insert(row, col, insertText)

        // Assert
        assertEquals(2, document.rows(), "Document should contain 2 rows after insertion.")
        assertEquals("Hello\n", document.getText(0), "First row should be 'Hello\\n'.")
        assertEquals("World", document.getText(1), "Second row should be 'World'.")
    }

    /**
     * Tests inserting text at various positions and verifies that the text is placed correctly.
     */
    @Test
    fun insert_atVariousPositions_insertsCorrectly() {
        // Arrange
        val document = DocumentImpl.create()
        document.insert(0, 0, "Hello World")

        // Act
        document.insert(0, 5, ",") // Insert comma after "Hello"
        document.insert(0, 6, " Beautiful") // Insert " Beautiful" after the comma

        // Assert
        assertEquals("Hello, Beautiful World", document.getText(0), "Text should reflect all insertions correctly.")
    }


    /**
     * Tests the document's row ending type in different environments.
     * Ensures that the row ending matches the platform's default.
     */
    @Test
    fun rowEnding_onDifferentPlatforms_returnsCorrectNewLine() {
        // Arrange
        val document = DocumentImpl.create()

        // Act
        val rowEnding = document.rowEnding()

        // Assert
        assertEquals(NewLine.platform, rowEnding, "Row ending should match the platform-specific newline.")
    }

    @Test
    fun `deleting newline at start of row merges lines`() {
        // Arrange
        val doc = DocumentImpl.create()

        // Insert "A\n" => row0
        doc.insert(0, 0, "A")
        doc.insert(0, 1, "\n")

        // Insert "B" => row1
        doc.insert(1, 0, "B")
        // So conceptually we have:
        // row0: "A\n"
        // row1: "B"

        // Act
        // Remove the newline from the perspective of row1,col=0
        // This *should* merge row1 ("B") up into row0, resulting in just "AB".
        doc.delete(1, 0, "\n")

        // Assert
        // We expect row0 = "AB", and only 1 row in the document
        assertEquals("AB", doc.getText(0), "Row 0 should be 'AB' after merging")
        assertEquals(1, doc.rows(), "Document should have merged into a single row")
    }

    @Test
    fun pos_mbytes_charsets() {
        // Arrange
        val document = DocumentImpl.create()
        // Text: "açb" (UTF-8: a=1 byte, ç=2 bytes, b=1 byte)
        // Character Positions: [0, 1, 2]
        // Byte Positions: [0, 1, 3, 4]
        val insertText = "açb"
        document.insert(0, 0, insertText)
        val serialNumber = 3L

        // Act
        val cursor = document.pos(serialNumber)

        // Assert
        assertEquals(0, cursor.row, "Cursor row should be 0 for serial 0.")
        assertEquals(2, cursor.col, "Cursor column should be 0 for serial 2.")
    }

    @Test
    fun `insert and retrieve multibyte characters`() {
        // Arrange
        val document = DocumentImpl.create()
        val insertText = "açb"  // UTF-8: 'a' (1 byte), 'ç' (2 bytes), 'b' (1 byte)
        document.insert(0, 0, insertText)

        // Act
        val retrievedText = document.getText(0)

        // Assert
        assertEquals(insertText, retrievedText, "Inserted text should match retrieved text.")
    }

    @Test
    fun `get byte array for multibyte characters`() {
        // Arrange
        val document = DocumentImpl.create()
        val insertText = "açb"
        document.insert(0, 0, insertText)

        // Act
        val byteArray = document.get(0)

        // Assert
        val expectedBytes = insertText.toByteArray(StandardCharsets.UTF_8)
        assertArrayEquals(expectedBytes, byteArray, "Byte array should match UTF-8 encoding.")
    }

    @Test
    fun `serial position for multibyte characters`() {
        // Arrange
        val document = DocumentImpl.create()
        document.insert(0, 0, "açb") // 'a' = 1 byte, 'ç' = 2 bytes, 'b' = 1 byte
        // Byte positions: 'a' (0), 'ç' (1-2), 'b' (3)
        val size = document.rawSize()

        // Act & Assert
        assertEquals(4, size, "Raw byte size should be 4.")
        assertEquals(0, document.serial(0, 0), "Serial of 'a' should be 0.")
        assertEquals(1, document.serial(0, 1), "Serial of 'ç' should be 1.")
        assertEquals(3, document.serial(0, 3), "Serial of 'b' should be 3.")
    }

    @Test
    fun `pos function for multibyte characters`() {
        // Arrange
        val document = DocumentImpl.create()
        document.insert(0, 0, "açb")

        // Act & Assert
        val caret1 = document.pos(0) // 'a'
        assertEquals(0, caret1.row)
        assertEquals(0, caret1.col)

        val caret2 = document.pos(1) // 'ç' (first byte)
        assertEquals(0, caret2.row)
        assertEquals(1, caret2.col)

        val caret3 = document.pos(3) // 'b'
        assertEquals(0, caret3.row)
        assertEquals(2, caret3.col)
    }

    @Test
    fun `delete multibyte character and check document state`() {
        // Arrange
        val document = DocumentImpl.create()
        document.insert(0, 0, "açb")

        // Act
        document.delete(0, 1, "ç") // Deleting 'ç' (2 bytes)

        // Assert
        assertEquals("ab", document.getText(0), "Text after deleting 'ç' should be 'ab'.")
        assertEquals(2, document.rawSize(), "Raw byte size should decrease by 2 after deletion.")
    }

    @Test
    fun `handling mixed ascii and multibyte characters`() {
        // Arrange
        val document = DocumentImpl.create()
        document.insert(0, 0, "Hello, 世界!") // "世界" is 6 bytes in UTF-8

        // Act
        val serialPos = document.serial(0, 7) // Position after 'Hello, ' should point to '世'

        // Assert
        assertEquals(7, serialPos, "Serial position should correctly track multi-byte characters.")
        val caret = document.pos(7)
        assertEquals(0, caret.row)
        assertEquals(7, caret.col, "Column position should match input position.")
    }

}
