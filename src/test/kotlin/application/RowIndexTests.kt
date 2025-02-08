package application

import org.editor.application.NewLine
import org.editor.application.RowIndex
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertThrows
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals

class RowIndexTests {

    @Test
    fun add_withSingleLine_addsCorrectly() {
        val rowIndex = RowIndex.create()  // Uses default newline (platform default)
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
        val rowIndex = RowIndex.create(NewLine.platform.bytes())
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

        // Verify internal cache if applicable.
        assertEquals(2, index.stCache().size, "Static cache size should be 2.")
        assertEquals(0, index.stCache()[0], "First cache entry should be 0.")
        assertEquals(12, index.stCache()[1], "Second cache entry should be 12.")
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
    fun rows_parsesRowsCorrectly() {
        var ret = RowIndex.rows("".toByteArray(StandardCharsets.UTF_8))
        Assertions.assertEquals(0, ret.size, "Empty input should result in 0 rows.")

        ret = RowIndex.rows("a".toByteArray(StandardCharsets.UTF_8))
        Assertions.assertEquals(1, ret.size, "Single character input should result in 1 row.")
        Assertions.assertEquals(1, ret[0], "Row length should be 1 for 'a'.")

        ret = RowIndex.rows("ab".toByteArray(StandardCharsets.UTF_8))
        Assertions.assertEquals(1, ret.size, "Two characters input should result in 1 row.")
        Assertions.assertEquals(2, ret[0], "Row length should be 2 for 'ab'.")

        ret = RowIndex.rows("ab\n".toByteArray(StandardCharsets.UTF_8))
        Assertions.assertEquals(2, ret.size, "Input ending with newline should result in 2 rows.")
        Assertions.assertEquals(3, ret[0], "First row length should be 3 for 'ab\\n'.")
        Assertions.assertEquals(0, ret[1], "Second row length should be 0 for empty second row.")

        ret = RowIndex.rows("\n".toByteArray(StandardCharsets.UTF_8))
        Assertions.assertEquals(2, ret.size, "Single newline should result in 2 rows.")
        Assertions.assertEquals(1, ret[0], "First row length should be 1 for '\\n'.")
        Assertions.assertEquals(0, ret[1], "Second row length should be 0 for empty second row.")

        ret = RowIndex.rows("\n\n".toByteArray(StandardCharsets.UTF_8))
        Assertions.assertEquals(3, ret.size, "Double newline should result in 3 rows.")
        Assertions.assertEquals(1, ret[0], "First row length should be 1 for '\\n'.")
        Assertions.assertEquals(1, ret[1], "Second row length should be 1 for '\\n'.")
        Assertions.assertEquals(0, ret[2], "Third row length should be 0 for empty third row.")

        // For CRLF newline we pass the newline bytes explicitly.
        ret = RowIndex.rows("abc\r\nde\r\nf".toByteArray(StandardCharsets.UTF_8), "\r\n".toByteArray(StandardCharsets.UTF_8))
        Assertions.assertEquals(3, ret.size, "Input with CRLF should result in 3 rows.")
        Assertions.assertEquals(5, ret[0], "First row length should be 5 for 'abc\\r\\n'.")
        Assertions.assertEquals(4, ret[1], "Second row length should be 4 for 'de\\r\\n'.")
        Assertions.assertEquals(1, ret[2], "Third row length should be 1 for 'f'.")
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

//    @Test
//    fun pos1_retrievesCorrectPositionsForSerialsWithinSingleLine() {
//        val index = RowIndex.create(3)
//        index.insert(0, 0, "abc".toByteArray(StandardCharsets.UTF_8))
//        assertArrayEquals(intArrayOf(0, 0)!!, index.pos(0)!!, "Position for serial 0 should be (0,0).")
//        assertArrayEquals(intArrayOf(0, 1), index.pos(1), "Position for serial 1 should be (0,1).")
//        assertArrayEquals(intArrayOf(0, 2), index.pos(2), "Position for serial 2 should be (0,2).")
//        assertArrayEquals(intArrayOf(0, 3), index.pos(3), "Position for serial 3 should be (0,3).")
//    }
//
//    @Test
//    fun pos2_retrievesCorrectPositionsForSerialsAcrossMultipleLines() {
//        val index = RowIndex.create(3)
//        index.insert(0, 0, "a\nbb\nccc\ndddd\neeeee".toByteArray(StandardCharsets.UTF_8))
//        assertArrayEquals(intArrayOf(0, 0), index.pos(0), "Position for serial 0 should be (0,0).")
//        assertArrayEquals(intArrayOf(0, 1), index.pos(1), "Position for serial 1 should be (0,1).")
//
//        assertArrayEquals(intArrayOf(1, 0), index.pos(2), "Position for serial 2 should be (1,0).")
//        assertArrayEquals(intArrayOf(1, 1), index.pos(3), "Position for serial 3 should be (1,1).")
//        assertArrayEquals(intArrayOf(1, 2), index.pos(4), "Position for serial 4 should be (1,2).")
//
//        assertArrayEquals(intArrayOf(2, 0), index.pos(5), "Position for serial 5 should be (2,0).")
//        assertArrayEquals(intArrayOf(2, 1), index.pos(6), "Position for serial 6 should be (2,1).")
//        assertArrayEquals(intArrayOf(2, 2), index.pos(7), "Position for serial 7 should be (2,2).")
//        assertArrayEquals(intArrayOf(2, 3), index.pos(8), "Position for serial 8 should be (2,3).")
//
//        assertArrayEquals(intArrayOf(3, 0), index.pos(9), "Position for serial 9 should be (3,0).")
//        assertArrayEquals(intArrayOf(3, 1), index.pos(10), "Position for serial 10 should be (3,1).")
//        assertArrayEquals(intArrayOf(3, 2), index.pos(11), "Position for serial 11 should be (3,2).")
//        assertArrayEquals(intArrayOf(3, 3), index.pos(12), "Position for serial 12 should be (3,3).")
//        assertArrayEquals(intArrayOf(3, 4), index.pos(13), "Position for serial 13 should be (3,4).")
//
//        assertArrayEquals(intArrayOf(4, 0), index.pos(14), "Position for serial 14 should be (4,0).")
//        assertArrayEquals(intArrayOf(4, 1), index.pos(15), "Position for serial 15 should be (4,1).")
//        assertArrayEquals(intArrayOf(4, 2), index.pos(16), "Position for serial 16 should be (4,2).")
//        assertArrayEquals(intArrayOf(4, 3), index.pos(17), "Position for serial 17 should be (4,3).")
//        assertArrayEquals(intArrayOf(4, 4), index.pos(18), "Position for serial 18 should be (4,4).")
//        assertArrayEquals(intArrayOf(4, 5), index.pos(19), "Position for serial 19 should be (4,5).")
//    }
}

