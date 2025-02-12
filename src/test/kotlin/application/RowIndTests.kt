package application

import org.editor.application.TextRowIndex
import org.junit.jupiter.api.Assertions.assertArrayEquals
import kotlin.test.Test
import kotlin.test.assertEquals

class RowIndexTests {

    @Test
    fun testUnixNewline() {
        // Using Unix newlines ("\n")
        val newline = "\n"
        val textRowIndex = TextRowIndex.create(newline, 100)
        val text = "Hello\nWorld\nTest"
        textRowIndex.add(text)

        // "Hello\n" is 6 characters, "World\n" is 6 characters, "Test" is 4 characters.
        val expectedRowLengths = intArrayOf(6, 6, 4)
        assertArrayEquals(expectedRowLengths, textRowIndex.rowLengths())
        assertEquals(3, textRowIndex.rowSize())

        // Verify serial positions:
        // Row0 starts at 0, row1 at 6, row2 at 12.
        assertEquals(0L, textRowIndex.serial(0, 0))
        assertEquals(6L, textRowIndex.serial(1, 0))
        assertEquals(12L, textRowIndex.serial(2, 0))

        // Test mapping: e.g. serial position 7 should be in row1, col=1 ("World\n")
        val (row, col) = textRowIndex.pos(7)
        assertEquals(1, row)
        assertEquals(1, col)
    }

    @Test
    fun testWindowsNewline() {
        // Using Windows newlines ("\r\n")
        val newline = "\r\n"
        val textRowIndex = TextRowIndex.create(newline, 100)
        val text = "Line1\r\nLine2\r\nLine3"
        textRowIndex.add(text)

        // "Line1\r\n" is 7 characters, "Line2\r\n" is 7 characters, "Line3" is 5 characters.
        val expectedRowLengths = intArrayOf(7, 7, 5)
        assertArrayEquals(expectedRowLengths, textRowIndex.rowLengths())
        assertEquals(3, textRowIndex.rowSize())

        // Verify serial positions:
        // Row0 starts at 0, row1 at 7, row2 at 14.
        assertEquals(0L, textRowIndex.serial(0, 0))
        assertEquals(7L, textRowIndex.serial(1, 0))
        assertEquals(14L, textRowIndex.serial(2, 0))

        // Check mapping: serial position 8 should be row1, col=1.
        val (row, col) = textRowIndex.pos(8)
        assertEquals(1, row)
        assertEquals(1, col)
    }

    @Test
    fun testOldMacNewline() {
        // Using old Mac newlines ("\r")
        val newline = "\r"
        val textRowIndex = TextRowIndex.create(newline, 100)
        val text = "One\rTwo\rThree"
        textRowIndex.add(text)

        // "One\r" is 4 characters, "Two\r" is 4 characters, "Three" is 5 characters.
        val expectedRowLengths = intArrayOf(4, 4, 5)
        assertArrayEquals(expectedRowLengths, textRowIndex.rowLengths())
        assertEquals(3, textRowIndex.rowSize())

        // Verify serial positions:
        // Row0 starts at 0, row1 at 4, row2 at 8.
        assertEquals(0L, textRowIndex.serial(0, 0))
        assertEquals(4L, textRowIndex.serial(1, 0))
        assertEquals(8L, textRowIndex.serial(2, 0))

        // Check mapping: serial position 5 should be row1, col=1.
        val (row, col) = textRowIndex.pos(5)
        assertEquals(1, row)
        assertEquals(1, col)
    }

    @Test
    fun testInsertOperation() {
        val newline = "\n"
        val textRowIndex = TextRowIndex.create(newline, 100)
        // Start with one insertion: "Hello\nWorld" → two rows.
        textRowIndex.add("Hello\nWorld")
        // Expect row0: "Hello\n" (6 chars) and row1: "World" (5 chars)
        assertArrayEquals(intArrayOf(6, 5), textRowIndex.rowLengths())

        // Insert text that includes a newline in row1 at column 2.
        // The inserted text "Test\n" will split into two rows:
        //   - First part: "Test\n" (5 characters)
        //   - Second part: "" (0 characters) because the text ends with a newline.
        // In insert(), the current row (row1) is split at col 2:
        //   head = col + first part = 2 + 5 = 7,
        //   tail = (rowLengths[1] - col) + second part = (5 - 2) + 0 = 3.
        // So the updated rows become:
        //   row0 remains 6, row1 becomes 7, and a new row (row2) becomes 3.
        textRowIndex.insert(1, 2, "Test\n")
        val expectedRowLengthsAfterInsert = intArrayOf(6, 7, 3)
        assertEquals(3, textRowIndex.rowSize())
        assertArrayEquals(expectedRowLengthsAfterInsert, textRowIndex.rowLengths())
    }

    @Test
    fun testDeleteOperation() {
        val newline = "\n"
        val textRowIndex = TextRowIndex.create(newline, 100)
        // Start with three rows using "Hello\nWorld\nTest":
        // row0: "Hello\n" → 6, row1: "World\n" → 6, row2: "Test" → 4.
        textRowIndex.add("Hello\nWorld\nTest")
        assertArrayEquals(intArrayOf(6, 6, 4), textRowIndex.rowLengths())

        // Delete 3 characters from row1 starting at column 2.
        // Since (rowLengths[1] - col) = 6 - 2 = 4 is greater than 3,
        // the deletion happens only within row1.
        textRowIndex.delete(1, 2, 3)
        // Expected row1 length becomes 6 - 3 = 3.
        val expectedRowLengthsAfterDelete = intArrayOf(6, 3, 4)
        assertArrayEquals(expectedRowLengthsAfterDelete, textRowIndex.rowLengths())
    }
}