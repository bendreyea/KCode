package application

import org.editor.application.common.LineSeparator
import org.editor.application.RowIndex
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertThrows
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals

class TextRowIndexTests {

    @Test
    fun add_withSingleLine_addsCorrectly() {
        val rowIndex = RowIndex.create()
        val text = "Hello".toByteArray(StandardCharsets.UTF_8)
        rowIndex.add(text)
        // Expecting one row with 5 bytes ("Hello")
        assertEquals(1, rowIndex.rowSize(), "Row size should be 1 after adding a single line.")
        assertEquals(5, rowIndex.rowLengths()[0], "First row length should be 5 for 'Hello'.")
    }

    @Test
    fun add_withMultipleLines_addsCorrectly() {
        val rowIndex = RowIndex.create()
        val text = "Hello\nWorld".toByteArray(StandardCharsets.UTF_8)
        rowIndex.add(text)
        // "Hello\n" is 6 bytes; "World" is 5 bytes.
        assertEquals(2, rowIndex.rowSize(), "Row size should be 2 after adding two lines.")
        assertEquals(6, rowIndex.rowLengths()[0], "First row length should be 6 for 'Hello\\n'.")
        assertEquals(5, rowIndex.rowLengths()[1], "Second row length should be 5 for 'World'.")
    }

    @Test
    fun get_withValidRow_returnsCorrectLength() {
        val rowIndex = RowIndex.create()
        rowIndex.add("Hello\nWorld".toByteArray(StandardCharsets.UTF_8))
        val length = rowIndex.get(1)
        assertEquals(6, length, "Length of the second row should be 6 for 'World'.")
    }

    @Test
    fun get_withInvalidRow_throwsException() {
        val rowIndex = RowIndex.create()
        rowIndex.add("Hello".toByteArray(StandardCharsets.UTF_8))
        val exception = assertThrows(IllegalArgumentException::class.java) {
            rowIndex.get(1)
        }
        assertEquals("Row index out of bounds: 1", exception.message, "Exception message should be correct.")
    }

    @Test
    fun insert_insertsTextCorrectly() {
        val rowIndex = RowIndex.create()
        rowIndex.add("Hello\nWorld".toByteArray(StandardCharsets.UTF_8))
        val insertText = "Beautiful ".toByteArray(StandardCharsets.UTF_8)
        val targetRow = 1
        val insertPosition = 0
        rowIndex.insert(targetRow, insertPosition, insertText)
        // "World" (5 bytes) with "Beautiful " (10 bytes) inserted becomes 15 bytes.
        assertEquals(2, rowIndex.rowSize(), "Row size should remain 2 after insertion within existing rows.")
        assertEquals(15, rowIndex.rowLengths()[1], "Second row length should be 15 after inserting 'Beautiful '.")
    }

    @Test
    fun delete_deletesTextCorrectly() {
        val rowIndex = RowIndex.create()
        rowIndex.add("Hello\nWorld".toByteArray(StandardCharsets.UTF_8))
        val deleteRow = 1
        val startCol = 0
        val lengthToDelete = 5
        rowIndex.delete(deleteRow, startCol, lengthToDelete)
        // After deletion, row 1 (originally "World") becomes empty.
        assertEquals(2, rowIndex.rowSize(), "Row size should remain 2 after deletion within existing rows.")
        assertEquals(0, rowIndex.rowLengths()[1], "Second row length should be 0 after deleting all characters.")
    }

    @Test
    fun delete_withMultipleLines_deletesCorrectly() {
        val rowIndex = RowIndex.create()
        rowIndex.add("Hello\nWorld\n!".toByteArray(StandardCharsets.UTF_8))
        // "Hello\n" (6 bytes), "World\n" (6 bytes), "!" (1 byte)
        // Deleting from row 0, column 5 (i.e. starting with the newline) for 7 bytes
        // removes the newline and the entire "World\n", merging row 0 with row 2.
        rowIndex.delete(0, 5, 7)
        assertEquals(1, rowIndex.rowSize(), "Row size should be 1 after deleting across multiple lines.")
        assertEquals(6, rowIndex.rowLengths()[0], "Remaining row length should be 6 after deletion.")
    }

    @Test
    fun serial_returnsCorrectSerial() {
        val rowIndex = RowIndex.create()
        rowIndex.add("Hello\nWorld".toByteArray(StandardCharsets.UTF_8))
        val serialNumber = rowIndex.serial(1, 0)
        // Serial for row 1, col 0 should equal the total bytes of row 0 (6 bytes).
        assertEquals(6, serialNumber, "Serial number for row 1, column 0 should be 6.")
    }

    @Test
    fun pos_returnsCorrectPosition() {
        // Create RowIndex with an explicit newline sequence.
        val rowIndex = RowIndex.create(LineSeparator.platform.bytes())
        rowIndex.add("Hello\nWorld".toByteArray(StandardCharsets.UTF_8))
        val serialNumber = 6L
        val (row, col) = rowIndex.pos(serialNumber)
        assertEquals(1, row, "Row should be 1 for serial number 6.")
        assertEquals(0, col, "Column should be 0 for serial number 6.")
    }

    @Test
    fun add_incrementalAdds_updatesRowsCorrectly() {
        val index = RowIndex.create()
        val texts = listOf(
            "a".toByteArray(StandardCharsets.UTF_8),
            "b".toByteArray(StandardCharsets.UTF_8),
            "\n".toByteArray(StandardCharsets.UTF_8),
            "cd".toByteArray(StandardCharsets.UTF_8),
            "ef\n\ngh".toByteArray(StandardCharsets.UTF_8)
        )
        // Expected row lengths: [3, 5, 1, 2]
        index.add(texts[0]) // "a"
        index.add(texts[1]) // "ab"
        index.add(texts[2]) // "ab\n" (row 0 becomes 3, new row starts)
        index.add(texts[3]) // row1 becomes "cd"
        index.add(texts[4]) // "ef\n\ngh" splits into [3, 1, 2] and adds to row1 and creates new rows.
        val expectedRowLengths = listOf(3, 5, 1, 2)
        assertEquals(4, index.rowLengths().size, "There should be 4 rows after all additions.")
        assertEquals(expectedRowLengths[0], index.rowLengths()[0], "First row length should be ${expectedRowLengths[0]}.")
        assertEquals(expectedRowLengths[1], index.rowLengths()[1], "Second row length should be ${expectedRowLengths[1]}.")
        assertEquals(expectedRowLengths[2], index.rowLengths()[2], "Third row length should be ${expectedRowLengths[2]}.")
        assertEquals(expectedRowLengths[3], index.rowLengths()[3], "Fourth row length should be ${expectedRowLengths[3]}.")
    }

    @Test
    fun get_complexRowIndex_returnsCorrectSerialNumbers() {
        val index = RowIndex.create(5)
        index.add("ab\n\ncde\nf\ng\nhi\njkl\nmn".toByteArray(StandardCharsets.UTF_8))
        assertEquals(0, index.get(0), "Serial number for row 0 should be 0.")
        assertEquals(3, index.get(1), "Serial number for row 1 should be 3.")
        assertEquals(4, index.get(2), "Serial number for row 2 should be 4.")
        assertEquals(8, index.get(3), "Serial number for row 3 should be 8.")
        assertEquals(10, index.get(4), "Serial number for row 4 should be 10.")
        assertEquals(12, index.get(5), "Serial number for row 5 should be 12.")
        assertEquals(15, index.get(6), "Serial number for row 6 should be 15.")
        assertEquals(19, index.get(7), "Serial number for row 7 should be 19.")
    }

    @Test
    fun insert_insertsTextAtSpecificPositions_updatesRowLengths() {
        val index = RowIndex.create(5)
        index.add("ab\ncde\nfg\nhij\nk".toByteArray(StandardCharsets.UTF_8))
        val insertText = "12\n24\n56".toByteArray(StandardCharsets.UTF_8)
        index.insert(1, 2, insertText)
        assertEquals(7, index.rowLengths().size, "There should be 7 rows after insertion.")
        assertEquals(3, index.rowLengths()[0], "First row length should be 3.")
        assertEquals(5, index.rowLengths()[1], "Second row length should be 5.")
        assertEquals(3, index.rowLengths()[2], "Third row length should be 3.")
        assertEquals(4, index.rowLengths()[3], "Fourth row length should be 4.")
        assertEquals(3, index.rowLengths()[4], "Fifth row length should be 3.")
        assertEquals(4, index.rowLengths()[5], "Sixth row length should be 4.")
        assertEquals(1, index.rowLengths()[6], "Seventh row length should be 1.")
    }

    @Test
    fun insertMono_insertsIntoEmptyRowIndex_correctly() {
        val index = RowIndex.create(5)
        val insertText = "abc".toByteArray(StandardCharsets.UTF_8)
        index.insert(0, 0, insertText)
        assertEquals(1, index.rowLengths().size, "There should be 1 row after insertion.")
        assertEquals(3, index.rowLengths()[0], "First row length should be 3 for 'abc'.")
    }

    @Test
    fun deleteEnd_deletesCharactersFromEnd_correctly() {
        val index = RowIndex.create(5)
        index.add("12".toByteArray(StandardCharsets.UTF_8))
        index.delete(0, 1, 1) // Delete character at position 1
        assertEquals(1, index.rowLengths().size, "Row size should remain 1 after deletion.")
        assertEquals(1, index.rowLengths()[0], "Row length should be 1 after deleting one character.")

        index.delete(0, 0, 1) // Delete character at position 0
        assertEquals(1, index.rowLengths().size, "Row size should remain 1 after deleting all characters.")
        assertEquals(0, index.rowLengths()[0], "Row length should be 0 after deleting all characters.")
    }

    @Test
    fun deleteEnd2_deletesCharactersFromEndWithNewline_correctly() {
        val index = RowIndex.create(5)
        index.add("abc\n12".toByteArray(StandardCharsets.UTF_8))
        index.delete(1, 1, 1) // Delete character at position 1 in row 1
        assertEquals(2, index.rowLengths().size, "Row size should remain 2 after deletion.")
        assertEquals(4, index.rowLengths()[0], "First row length should be 4 after deletion.")
        assertEquals(1, index.rowLengths()[1], "Second row length should be 1 after deletion.")
    }

    @Test
    fun deleteWithinSingleLine_deletesCharactersCorrectly() {
        val index = RowIndex.create(5)
        index.add("abcd\n".toByteArray(StandardCharsets.UTF_8))
        index.delete(0, 1, 2) // Delete characters from column 1 to 2
        // "abcd\n" becomes "ad\n" → row0 length = 3; row1 is the empty row after newline.
        assertEquals(2, index.rowLengths().size, "Row size should remain 2 after deletion.")
        assertEquals(3, index.rowLengths()[0], "First row length should be 3 after deletion.")
        assertEquals(0, index.rowLengths()[1], "Second row length should be 0 after deletion.")

        index.delete(0, 0, 3) // Delete all remaining characters
        assertEquals(1, index.rowLengths().size, "Row size should be 1 after deleting all characters.")
        assertEquals(0, index.rowLengths()[0], "First row length should be 0 after deleting all characters.")
    }

    @Test
    fun deleteAcrossMultipleLines_deletesCorrectly() {
        val index = RowIndex.create(5)
        index.add("ab\ncd\nef\ngh\n".toByteArray(StandardCharsets.UTF_8))
        index.delete(0, 1, 6) // Delete from row 0, column 1, length 6
        assertEquals(3, index.rowLengths().size, "Row size should be 3 after deletion across multiple lines.")
        assertEquals(3, index.rowLengths()[0], "First row length should be 3 after deletion.")
        assertEquals(3, index.rowLengths()[1], "Second row length should be 3 after deletion.")
        assertEquals(0, index.rowLengths()[2], "Third row length should be 0 after deletion.")
    }

    @Test
    fun rowSizeWithInsertAndDelete_updatesCorrectly() {
        val index = RowIndex.create(5)
        assertEquals(1, index.rowSize(), "Initial row size should be 1.")

        index.insert(0, 0, "aaa\nbbb\nccc".toByteArray(StandardCharsets.UTF_8))
        assertEquals(3, index.rowSize(), "Row size should be 3 after insertion.")
        val rowLengths = index.rowLengths()
        Assertions.assertEquals(3, rowLengths.size, "There should be 3 rows after insertion.")
        Assertions.assertEquals(4, rowLengths[0], "First row length should be 4.")
        Assertions.assertEquals(4, rowLengths[1], "Second row length should be 4.")
        Assertions.assertEquals(3, rowLengths[2], "Third row length should be 3.")

        assertEquals(0, index.get(0), "Serial number for row 0 should be 0.")
        assertEquals(4, index.get(1), "Serial number for row 1 should be 4.")
        assertEquals(8, index.get(2), "Serial number for row 2 should be 8.")

        index.delete(0, 0, 8) // Delete first 8 characters
        assertEquals(1, index.rowSize(), "Row size should be 1 after deletion.")
        val newRowLengths = index.rowLengths()
        Assertions.assertEquals(1, newRowLengths.size, "There should be 1 row after deletion.")
        Assertions.assertEquals(3, newRowLengths[0], "Row length should be 3 after deletion.")
        assertEquals(0, index.get(0), "Serial number for row 0 should be 0 after deletion.")
    }

    @Test
    fun rowSizeWithInsertAndDelete2_updatesCorrectly() {
        val index = RowIndex.create(5)
        assertEquals(1, index.rowSize(), "Initial row size should be 1.")

        index.insert(0, 0, "aaa\nbbb\nccc".toByteArray(StandardCharsets.UTF_8))
        assertEquals(3, index.rowSize(), "Row size should be 3 after insertion.")

        index.delete(0, 1, 8) // Delete from row 0, column 1, length 8
        assertEquals(1, index.rowSize(), "Row size should be 1 after deletion.")
        val rowLengths = index.rowLengths()
        Assertions.assertEquals(1, rowLengths.size, "There should be 1 row after deletion.")
        Assertions.assertEquals(3, rowLengths[0], "Row length should be 3 after deletion.")
        assertEquals(0, index.get(0), "Serial number for row 0 should be 0 after deletion.")
    }

    @Test
    fun rowSizeWithInsertAndDelete3_updatesCorrectly() {
        val index = RowIndex.create(5)
        assertEquals(1, index.rowSize(), "Initial row size should be 1.")

        index.insert(0, 0, "aaa\nbbb\nccc".toByteArray(StandardCharsets.UTF_8))
        assertEquals(3, index.rowSize(), "Row size should be 3 after insertion.")

        index.delete(0, 3, 8) // Delete from row 0, column 3, length 8
        assertEquals(1, index.rowSize(), "Row size should be 1 after deletion.")
        val rowLengths = index.rowLengths()
        Assertions.assertEquals(1, rowLengths.size, "There should be 1 row after deletion.")
        Assertions.assertEquals(3, rowLengths[0], "Row length should be 3 after deletion.")
        assertEquals(0, index.get(0), "Serial number for row 0 should be 0 after deletion.")
    }

    @Test
    fun rowSizeWithInsertAndDelete4_updatesCorrectly() {
        val index = RowIndex.create(5)
        assertEquals(1, index.rowSize(), "Initial row size should be 1.")

        index.insert(0, 0, "aaa\nbbb\nccc".toByteArray(StandardCharsets.UTF_8))
        assertEquals(3, index.rowSize(), "Row size should be 3 after insertion.")

        index.delete(1, 0, 7) // Delete from row 1, column 0, length 7
        assertEquals(2, index.rowSize(), "Row size should be 2 after deletion.")
        val rowLengths = index.rowLengths()
        Assertions.assertEquals(2, rowLengths.size, "There should be 2 rows after deletion.")
        Assertions.assertEquals(4, rowLengths[0], "First row length should be 4 after deletion.")
        Assertions.assertEquals(0, rowLengths[1], "Second row length should be 0 after deletion.")
        assertEquals(0, index.get(0), "Serial number for row 0 should be 0.")
        assertEquals(4, index.get(1), "Serial number for row 1 should be 4.")
    }

    @Test
    fun serial_computesSerialNumbersCorrectly() {
        val index = RowIndex.create(3)
        index.insert(0, 0, "a\nbb\nccc\ndddd\neeeee".toByteArray(StandardCharsets.UTF_8))
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
}

