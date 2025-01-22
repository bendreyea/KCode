package application

import org.editor.application.RowIndex
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertThrows
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals

class RowIndexTests {

    /**
     * Tests adding a single line of text to the RowIndex.
     * Ensures that the row size and row lengths are updated correctly.
     */
    @Test
    fun add_withSingleLine_addsCorrectly() {
        // Arrange
        val rowIndex = RowIndex.create()
        val text = "Hello".toByteArray()

        // Act
        rowIndex.add(text)
        val t = rowIndex.get(0)

        // Assert
        assertEquals(1, rowIndex.rowSize(), "Row size should be 1 after adding a single line.")
        assertEquals(5, rowIndex.rowLengths()[0], "First row length should be 5 for 'Hello'.")
    }

    /**
     * Tests adding multiple lines of text to the RowIndex.
     * Verifies that the row size and individual row lengths are accurately reflected.
     */
    @Test
    fun add_withMultipleLines_addsCorrectly() {
        // Arrange
        val rowIndex = RowIndex.create()
        val text = "Hello\nWorld".toByteArray()

        // Act
        rowIndex.add(text)

        // Assert
        assertEquals(2, rowIndex.rowSize(), "Row size should be 2 after adding two lines.")
        assertEquals(6, rowIndex.rowLengths()[0], "First row length should be 6 for 'Hello\\n'.")
        assertEquals(5, rowIndex.rowLengths()[1], "Second row length should be 5 for 'World'.")
    }

    /**
     * Tests retrieving the length of a valid row.
     * Ensures that the correct length is returned.
     */
    @Test
    fun get_withValidRow_returnsCorrectLength() {
        // Arrange
        val rowIndex = RowIndex.create()
        rowIndex.add("Hello\nWorld".toByteArray())

        // Act
        val length = rowIndex.get(1)

        // Assert
        assertEquals(6, length, "Length of the second row should be 6 for 'World'.")
    }

    /**
     * Tests retrieving the length of an invalid row.
     * Expects an IllegalArgumentException to be thrown.
     */
    @Test
    fun get_withInvalidRow_throwsException() {
        // Arrange
        val rowIndex = RowIndex.create()
        rowIndex.add("Hello".toByteArray())

        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            rowIndex.get(1)
        }.also {
            assertEquals("Row index out of bounds: 1", it.message, "Exception message should be correct.")
        }
    }

    /**
     * Tests inserting text into an existing row.
     * Verifies that the row length is updated correctly after insertion.
     */
    @Test
    fun insert_insertsTextCorrectly() {
        // Arrange
        val rowIndex = RowIndex.create()
        rowIndex.add("Hello\nWorld".toByteArray())
        val insertText = "Beautiful ".toByteArray()
        val targetRow = 1
        val insertPosition = 0

        // Act
        rowIndex.insert(targetRow, insertPosition, insertText)

        // Assert
        assertEquals(2, rowIndex.rowSize(), "Row size should remain 2 after insertion within existing rows.")
        assertEquals(15, rowIndex.rowLengths()[1], "Second row length should be 15 after inserting 'Beautiful '.")
    }

    /**
     * Tests deleting text from a specific position within a row.
     * Ensures that the row lengths are updated accordingly.
     */
    @Test
    fun delete_deletesTextCorrectly() {
        // Arrange
        val rowIndex = RowIndex.create()
        rowIndex.add("Hello\nWorld".toByteArray())
        val deleteRow = 1
        val startCol = 0
        val lengthToDelete = 5

        // Act
        rowIndex.delete(deleteRow, startCol, lengthToDelete)

        // Assert
        assertEquals(2, rowIndex.rowSize(), "Row size should remain 2 after deletion within existing rows.")
        assertEquals(0, rowIndex.rowLengths()[1], "Second row length should be 0 after deleting all characters.")
    }

    /**
     * Tests deleting text that spans multiple lines.
     * Verifies that the rows are merged correctly and lengths are updated.
     */
    @Test
    fun delete_withMultipleLines_deletesCorrectly() {
        // Arrange
        val rowIndex = RowIndex.create()
        rowIndex.add("Hello\nWorld\n!".toByteArray())
        val deleteStartRow = 0
        val deleteStartCol = 5
        val deleteEndSerial = 7

        // Act
        rowIndex.delete(deleteStartRow, deleteStartCol, deleteEndSerial)

        // Assert
        assertEquals(1, rowIndex.rowSize(), "Row size should be 1 after deleting across multiple lines.")
        assertEquals(6, rowIndex.rowLengths()[0], "Remaining row length should be 6 after deletion.")
    }

    /**
     * Tests the serial method to ensure it returns the correct serial number for a given row and column.
     */
    @Test
    fun serial_returnsCorrectSerial() {
        // Arrange
        val rowIndex = RowIndex.create()
        rowIndex.add("Hello\nWorld".toByteArray())

        // Act
        val serialNumber = rowIndex.serial(1, 0)

        // Assert
        assertEquals(6, serialNumber, "Serial number for row 1, column 0 should be 6.")
    }

    /**
     * Tests the pos method to retrieve the row and column from a given serial number.
     */
    @Test
    fun pos_returnsCorrectPosition() {
        // Arrange
        val rowIndex = RowIndex.create()
        rowIndex.add("Hello\nWorld".toByteArray())
        val serialNumber = 6L

        // Act
        val position = rowIndex.pos(serialNumber)

        // Assert
        assertEquals(1, position[0], "Row should be 1 for serial number 6.")
        assertEquals(0, position[1], "Column should be 0 for serial number 6.")
    }

    /**
     * Tests the add method with multiple incremental additions.
     * Ensures that rows and their lengths are updated correctly after each addition.
     */
    @Test
    fun add_incrementalAdds_updatesRowsCorrectly() {
        // Arrange
        val index = RowIndex.create()
        val texts = listOf(
            "a".toByteArray(),
            "b".toByteArray(),
            "\n".toByteArray(),
            "cd".toByteArray(),
            "ef\n\ngh".toByteArray()
        )
        val expectedRowLengths = listOf(
            3, // "ab$"
            5, // "cd"
            1, // "$"
            2  // "gh"
        )

        // Act
        index.add(texts[0]) // "a"
        index.add(texts[1]) // "ab"
        index.add(texts[2]) // "ab$"
        index.add(texts[3]) // "ab$cd"
        index.add(texts[4]) // "ab$cdef$$gh"

        // Assert
        assertEquals(4, index.rowLengths().size, "There should be 4 rows after all additions.")
        assertEquals(3, index.rowLengths()[0], "First row length should be 3.")
        assertEquals(5, index.rowLengths()[1], "Second row length should be 5.")
        assertEquals(1, index.rowLengths()[2], "Third row length should be 1.")
        assertEquals(2, index.rowLengths()[3], "Fourth row length should be 2.")
    }

    /**
     * Tests the get method with a complex RowIndex setup.
     * Verifies that the get method returns correct serial numbers for various rows.
     */
    @Test
    fun get_complexRowIndex_returnsCorrectSerialNumbers() {
        // Arrange
        val index = RowIndex.create(5)
        index.add("ab\n\ncde\nf\ng\nhi\njkl\nmn".toByteArray())

        // Act & Assert
        assertEquals(0, index.get(0), "Serial number for row 0 should be 0.")
        assertEquals(3, index.get(1), "Serial number for row 1 should be 3.")
        assertEquals(4, index.get(2), "Serial number for row 2 should be 4.")
        assertEquals(8, index.get(3), "Serial number for row 3 should be 8.")
        assertEquals(10, index.get(4), "Serial number for row 4 should be 10.")
        assertEquals(12, index.get(5), "Serial number for row 5 should be 12.")
        assertEquals(15, index.get(6), "Serial number for row 6 should be 15.")
        assertEquals(19, index.get(7), "Serial number for row 7 should be 19.")

        // Verify internal cache if applicable
        assertEquals(2, index.stCache().size, "Static cache size should be 2.")
        assertEquals(0, index.stCache()[0], "First cache entry should be 0.")
        assertEquals(12, index.stCache()[1], "Second cache entry should be 12.")
    }

    /**
     * Tests inserting text into specific positions and verifies the row lengths after insertion.
     */
    @Test
    fun insert_insertsTextAtSpecificPositions_updatesRowLengths() {
        // Arrange
        val index = RowIndex.create(5)
        index.add("ab\ncde\nfg\nhij\nk".toByteArray())

        // Act
        val insertText = "12\n24\n56".toByteArray()
        index.insert(1, 2, insertText)

        // Assert
        assertEquals(7, index.rowLengths().size, "There should be 7 rows after insertion.")
        assertEquals(3, index.rowLengths()[0], "First row length should be 3.")
        assertEquals(5, index.rowLengths()[1], "Second row length should be 5.")
        assertEquals(3, index.rowLengths()[2], "Third row length should be 3.")
        assertEquals(4, index.rowLengths()[3], "Fourth row length should be 4.")
        assertEquals(3, index.rowLengths()[4], "Fifth row length should be 3.")
        assertEquals(4, index.rowLengths()[5], "Sixth row length should be 4.")
        assertEquals(1, index.rowLengths()[6], "Seventh row length should be 1.")
    }

    /**
     * Tests inserting text into an empty RowIndex and verifies the row lengths.
     */
    @Test
    fun insertMono_insertsIntoEmptyRowIndex_correctly() {
        // Arrange
        val index = RowIndex.create(5)
        val insertText = "abc".toByteArray()

        // Act
        index.insert(0, 0, insertText)

        // Assert
        assertEquals(1, index.rowLengths().size, "There should be 1 row after insertion.")
        assertEquals(3, index.rowLengths()[0], "First row length should be 3 for 'abc'.")
    }

    /**
     * Tests deleting characters from the end of a row and verifies the row lengths.
     */
    @Test
    fun deleteEnd_deletesCharactersFromEnd_correctly() {
        // Arrange
        val index = RowIndex.create(5)
        index.add("12".toByteArray())

        // Act
        index.delete(0, 1, 1) // Delete character at position 1

        // Assert
        assertEquals(1, index.rowLengths().size, "Row size should remain 1 after deletion.")
        assertEquals(1, index.rowLengths()[0], "Row length should be 1 after deleting one character.")

        // Act
        index.delete(0, 0, 1) // Delete character at position 0

        // Assert
        assertEquals(1, index.rowLengths().size, "Row size should remain 1 after deleting all characters.")
        assertEquals(0, index.rowLengths()[0], "Row length should be 0 after deleting all characters.")
    }

    /**
     * Tests deleting characters from the end of a row that contains a newline character.
     * Verifies that the row lengths are updated appropriately.
     */
    @Test
    fun deleteEnd2_deletesCharactersFromEndWithNewline_correctly() {
        // Arrange
        val index = RowIndex.create(5)
        index.add("abc\n12".toByteArray())

        // Act
        index.delete(1, 1, 1) // Delete character at position 1 in row 1

        // Assert
        assertEquals(2, index.rowLengths().size, "Row size should remain 2 after deletion.")
        assertEquals(4, index.rowLengths()[0], "First row length should be 4 after deletion.")
        assertEquals(1, index.rowLengths()[1], "Second row length should be 1 after deletion.")
    }

    /**
     * Tests deleting characters within a single line.
     * Ensures that the row lengths are updated correctly after deletion.
     */
    @Test
    fun deleteWithinSingleLine_deletesCharactersCorrectly() {
        // Arrange
        val index = RowIndex.create(5)
        index.add("abcd\n".toByteArray())

        // Act
        index.delete(0, 1, 2) // Delete characters from column 1 to 2

        // Assert
        // Original: abcd$
        // After deletion: ad$
        assertEquals(2, index.rowLengths().size, "Row size should remain 2 after deletion.")
        assertEquals(3, index.rowLengths()[0], "First row length should be 3 after deletion.")
        assertEquals(0, index.rowLengths()[1], "Second row length should be 0 after deletion.")

        // Act
        index.delete(0, 0, 3) // Delete all remaining characters

        // Assert
        assertEquals(1, index.rowLengths().size, "Row size should be 1 after deleting all characters.")
        assertEquals(0, index.rowLengths()[0], "First row length should be 0 after deleting all characters.")
    }

    /**
     * Tests deleting characters across multiple lines.
     * Verifies that rows are merged correctly and lengths are updated.
     */
    @Test
    fun deleteAcrossMultipleLines_deletesCorrectly() {
        // Arrange
        val index = RowIndex.create(5)
        index.add("ab\ncd\nef\ngh\n".toByteArray())

        // Act
        index.delete(0, 1, 6) // Delete from row 0, column 1, length 6

        // Assert
        assertEquals(3, index.rowLengths().size, "Row size should be 3 after deletion across multiple lines.")
        assertEquals(3, index.rowLengths()[0], "First row length should be 3 after deletion.")
        assertEquals(3, index.rowLengths()[1], "Second row length should be 3 after deletion.")
        assertEquals(0, index.rowLengths()[2], "Third row length should be 0 after deletion.")
    }

    /**
     * Tests the rowSize method after performing insertions and deletions.
     * Ensures that the row size reflects the current number of rows accurately.
     */
    @Test
    fun rowSizeWithInsertAndDelete_updatesCorrectly() {
        // Arrange
        val index = RowIndex.create(5)
        assertEquals(1, index.rowSize(), "Initial row size should be 1.")

        // Act
        index.insert(0, 0, "aaa\nbbb\nccc".toByteArray(StandardCharsets.UTF_8))

        // Assert
        assertEquals(3, index.rowSize(), "Row size should be 3 after insertion.")
        var rowLengths: IntArray = index.rowLengths()
        Assertions.assertEquals(3, rowLengths.size, "There should be 3 rows after insertion.")
        Assertions.assertEquals(4, rowLengths[0], "First row length should be 4.")
        Assertions.assertEquals(4, rowLengths[1], "Second row length should be 4.")
        Assertions.assertEquals(3, rowLengths[2], "Third row length should be 3.")

        // Verify serial numbers
        assertEquals(0, index.get(0), "Serial number for row 0 should be 0.")
        assertEquals(4, index.get(1), "Serial number for row 1 should be 4.")
        assertEquals(8, index.get(2), "Serial number for row 2 should be 8.")

        // Act
        index.delete(0, 0, 8) // Delete first 8 characters

        // Assert
        // Original text: aaa\nbbb\nccc
        // After deletion: ccc
        assertEquals(1, index.rowSize(), "Row size should be 1 after deletion.")
        rowLengths = index.rowLengths()
        Assertions.assertEquals(1, rowLengths.size, "There should be 1 row after deletion.")
        Assertions.assertEquals(3, rowLengths[0], "Row length should be 3 after deletion.")
        assertEquals(0, index.get(0), "Serial number for row 0 should be 0 after deletion.")
    }

    /**
     * Tests the rowSize method with another insertion and deletion scenario.
     * Ensures that the row size is updated correctly based on different operations.
     */
    @Test
    fun rowSizeWithInsertAndDelete2_updatesCorrectly() {
        // Arrange
        val index = RowIndex.create(5)
        assertEquals(1, index.rowSize(), "Initial row size should be 1.")

        // Act
        index.insert(0, 0, "aaa\nbbb\nccc".toByteArray(StandardCharsets.UTF_8))

        // Assert
        assertEquals(3, index.rowSize(), "Row size should be 3 after insertion.")

        // Act
        index.delete(0, 1, 8) // Delete from row 0, column 1, length 8

        // Assert
        // Original text: aaa\nbbb\nccc
        // After deletion: acc
        assertEquals(1, index.rowSize(), "Row size should be 1 after deletion.")
        val rowLengths: IntArray = index.rowLengths()
        Assertions.assertEquals(1, rowLengths.size, "There should be 1 row after deletion.")
        Assertions.assertEquals(3, rowLengths[0], "Row length should be 3 after deletion.")
        assertEquals(0, index.get(0), "Serial number for row 0 should be 0 after deletion.")
    }

    /**
     * Tests the rowSize method with a third insertion and deletion scenario.
     * Validates that multiple operations correctly update the row size.
     */
    @Test
    fun rowSizeWithInsertAndDelete3_updatesCorrectly() {
        // Arrange
        val index = RowIndex.create(5)
        assertEquals(1, index.rowSize(), "Initial row size should be 1.")

        // Act
        index.insert(0, 0, "aaa\nbbb\nccc".toByteArray(StandardCharsets.UTF_8))

        // Assert
        assertEquals(3, index.rowSize(), "Row size should be 3 after insertion.")

        // Act
        index.delete(0, 3, 8) // Delete from row 0, column 3, length 8

        // Assert
        // Original text: aaa\nbbb\nccc
        // After deletion: aaa remains
        assertEquals(1, index.rowSize(), "Row size should be 1 after deletion.")
        val rowLengths: IntArray = index.rowLengths()
        Assertions.assertEquals(1, rowLengths.size, "There should be 1 row after deletion.")
        Assertions.assertEquals(3, rowLengths[0], "Row length should be 3 after deletion.")
        assertEquals(0, index.get(0), "Serial number for row 0 should be 0 after deletion.")
    }

    /**
     * Tests the rowSize method with a fourth insertion and deletion scenario.
     * Ensures that the row size reflects the changes accurately after complex operations.
     */
    @Test
    fun rowSizeWithInsertAndDelete4_updatesCorrectly() {
        // Arrange
        val index = RowIndex.create(5)
        assertEquals(1, index.rowSize(), "Initial row size should be 1.")

        // Act
        index.insert(0, 0, "aaa\nbbb\nccc".toByteArray(StandardCharsets.UTF_8))

        // Assert
        assertEquals(3, index.rowSize(), "Row size should be 3 after insertion.")

        // Act
        index.delete(1, 0, 7) // Delete from row 1, column 0, length 7

        // Assert
        // Original text: aaa\nbbb\nccc
        // After deletion: aaa$
        assertEquals(2, index.rowSize(), "Row size should be 2 after deletion.")
        val rowLengths: IntArray = index.rowLengths()
        Assertions.assertEquals(2, rowLengths.size, "There should be 2 rows after deletion.")
        Assertions.assertEquals(4, rowLengths[0], "First row length should be 4 after deletion.")
        Assertions.assertEquals(0, rowLengths[1], "Second row length should be 0 after deletion.")
        assertEquals(0, index.get(0), "Serial number for row 0 should be 0.")
        assertEquals(4, index.get(1), "Serial number for row 1 should be 4.")
    }

    /**
     * Tests the rows static method with various input scenarios.
     * Ensures that the rows are parsed correctly based on the input byte arrays.
     */
    @Test
    fun rows_parsesRowsCorrectly() {
        // Arrange & Act & Assert
        var ret = RowIndex.rows("".toByteArray())
        Assertions.assertEquals(0, ret.size, "Empty input should result in 0 rows.")

        ret = RowIndex.rows("a".toByteArray())
        Assertions.assertEquals(1, ret.size, "Single character input should result in 1 row.")
        Assertions.assertEquals(1, ret[0], "Row length should be 1 for 'a'.")

        ret = RowIndex.rows("ab".toByteArray())
        Assertions.assertEquals(1, ret.size, "Two characters input should result in 1 row.")
        Assertions.assertEquals(2, ret[0], "Row length should be 2 for 'ab'.")

        ret = RowIndex.rows("ab\n".toByteArray())
        Assertions.assertEquals(2, ret.size, "Input ending with newline should result in 2 rows.")
        Assertions.assertEquals(3, ret[0], "First row length should be 3 for 'ab\\n'.")
        Assertions.assertEquals(0, ret[1], "Second row length should be 0 for empty second row.")

        ret = RowIndex.rows("\n".toByteArray())
        Assertions.assertEquals(2, ret.size, "Single newline should result in 2 rows.")
        Assertions.assertEquals(1, ret[0], "First row length should be 1 for '\\n'.")
        Assertions.assertEquals(0, ret[1], "Second row length should be 0 for empty second row.")

        ret = RowIndex.rows("\n\n".toByteArray())
        Assertions.assertEquals(3, ret.size, "Double newline should result in 3 rows.")
        Assertions.assertEquals(1, ret[0], "First row length should be 1 for '\\n'.")
        Assertions.assertEquals(1, ret[1], "Second row length should be 1 for '\\n'.")
        Assertions.assertEquals(0, ret[2], "Third row length should be 0 for empty third row.")

        ret = RowIndex.rows("abc\r\nde\r\nf".toByteArray())
        Assertions.assertEquals(3, ret.size, "Input with CRLF should result in 3 rows.")
        Assertions.assertEquals(5, ret[0], "First row length should be 5 for 'abc\\r\\n'.")
        Assertions.assertEquals(4, ret[1], "Second row length should be 4 for 'de\\r\\n'.")
        Assertions.assertEquals(1, ret[2], "Third row length should be 1 for 'f'.")
    }

    /**
     * Tests the serial method with multiple rows and columns.
     * Ensures that the serial numbers are computed correctly.
     */
    @Test
    fun serial_computesSerialNumbersCorrectly() {
        // Arrange
        val index = RowIndex.create(3)
        index.insert(0, 0, "a\nbb\nccc\ndddd\neeeee".toByteArray(StandardCharsets.UTF_8))

        // Act & Assert
        assertEquals(0L, index.serial(0, 0), "Serial for (0,0) should be 0.")
        assertEquals(1L, index.serial(0, 1), "Serial for (0,1) should be 1.")

        assertEquals(2L, index.serial(1, 0), "Serial for (1,0) should be 2.")
        assertEquals(3L, index.serial(1, 1), "Serial for (1,1) should be 3.")
        assertEquals(4L, index.serial(1, 2), "Serial for (1,2) should be 4.")

        assertEquals(5L, index.serial(2, 0), "Serial for (2,0) should be 5.")
        assertEquals(6L, index.serial(2, 1), "Serial for (2,1) should be 6.")
        assertEquals(7L, index.serial(2, 2), "Serial for (2,2) should be 7.")
        assertEquals(8L, index.serial(2, 3), "Serial for (2,3) should be 8.")

        assertEquals(9L, index.serial(3, 0), "Serial for (3,0) should be 9.")
        assertEquals(10L, index.serial(3, 1), "Serial for (3,1) should be 10.")
        assertEquals(11L, index.serial(3, 2), "Serial for (3,2) should be 11.")
        assertEquals(12L, index.serial(3, 3), "Serial for (3,3) should be 12.")
        assertEquals(13L, index.serial(3, 4), "Serial for (3,4) should be 13.")

        assertEquals(14L, index.serial(4, 0), "Serial for (4,0) should be 14.")
        assertEquals(15L, index.serial(4, 1), "Serial for (4,1) should be 15.")
        assertEquals(16L, index.serial(4, 2), "Serial for (4,2) should be 16.")
        assertEquals(17L, index.serial(4, 3), "Serial for (4,3) should be 17.")
        assertEquals(18L, index.serial(4, 4), "Serial for (4,4) should be 18.")
        assertEquals(19L, index.serial(4, 5), "Serial for (4,5) should be 19.")
    }

    /**
     * Tests the pos method for serial numbers within a single line.
     * Ensures that the correct row and column are returned for each serial.
     */
    @Test
    fun pos1_retrievesCorrectPositionsForSerialsWithinSingleLine() {
        // Arrange
        val index = RowIndex.create(3)
        index.insert(0, 0, "abc".toByteArray(StandardCharsets.UTF_8))

        // Act & Assert
        assertArrayEquals(intArrayOf(0, 0), index.pos(0), "Position for serial 0 should be (0,0).")
        assertArrayEquals(intArrayOf(0, 1), index.pos(1), "Position for serial 1 should be (0,1).")
        assertArrayEquals(intArrayOf(0, 2), index.pos(2), "Position for serial 2 should be (0,2).")
        assertArrayEquals(intArrayOf(0, 3), index.pos(3), "Position for serial 3 should be (0,3).")
    }

    /**
     * Tests the pos method for serial numbers across multiple lines.
     * Ensures that the correct row and column are returned for each serial.
     */
    @Test
    fun pos2_retrievesCorrectPositionsForSerialsAcrossMultipleLines() {
        // Arrange
        val index = RowIndex.create(3)
        index.insert(0, 0, "a\nbb\nccc\ndddd\neeeee".toByteArray(StandardCharsets.UTF_8))

        // Act & Assert
        assertArrayEquals(intArrayOf(0, 0), index.pos(0), "Position for serial 0 should be (0,0).")
        assertArrayEquals(intArrayOf(0, 1), index.pos(1), "Position for serial 1 should be (0,1).")

        assertArrayEquals(intArrayOf(1, 0), index.pos(2), "Position for serial 2 should be (1,0).")
        assertArrayEquals(intArrayOf(1, 1), index.pos(3), "Position for serial 3 should be (1,1).")
        assertArrayEquals(intArrayOf(1, 2), index.pos(4), "Position for serial 4 should be (1,2).")

        assertArrayEquals(intArrayOf(2, 0), index.pos(5), "Position for serial 5 should be (2,0).")
        assertArrayEquals(intArrayOf(2, 1), index.pos(6), "Position for serial 6 should be (2,1).")
        assertArrayEquals(intArrayOf(2, 2), index.pos(7), "Position for serial 7 should be (2,2).")
        assertArrayEquals(intArrayOf(2, 3), index.pos(8), "Position for serial 8 should be (2,3).")

        assertArrayEquals(intArrayOf(3, 0), index.pos(9), "Position for serial 9 should be (3,0).")
        assertArrayEquals(intArrayOf(3, 1), index.pos(10), "Position for serial 10 should be (3,1).")
        assertArrayEquals(intArrayOf(3, 2), index.pos(11), "Position for serial 11 should be (3,2).")
        assertArrayEquals(intArrayOf(3, 3), index.pos(12), "Position for serial 12 should be (3,3).")
        assertArrayEquals(intArrayOf(3, 4), index.pos(13), "Position for serial 13 should be (3,4).")

        assertArrayEquals(intArrayOf(4, 0), index.pos(14), "Position for serial 14 should be (4,0).")
        assertArrayEquals(intArrayOf(4, 1), index.pos(15), "Position for serial 15 should be (4,1).")
        assertArrayEquals(intArrayOf(4, 2), index.pos(16), "Position for serial 16 should be (4,2).")
        assertArrayEquals(intArrayOf(4, 3), index.pos(17), "Position for serial 17 should be (4,3).")
        assertArrayEquals(intArrayOf(4, 4), index.pos(18), "Position for serial 18 should be (4,4).")
        assertArrayEquals(intArrayOf(4, 5), index.pos(19), "Position for serial 19 should be (4,5).")
    }
}
