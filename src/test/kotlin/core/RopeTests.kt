package core

import org.editor.core.Rope
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RopeTests {

    @Test
    fun testBuildRopeFromString() {
        val rope = Rope.buildRopeFromByteArray("Hello, Rope!".toByteArray())
        assertEquals(12, rope.length())
        assertEquals("Hello, Rope!", rope.toString())
    }


    @Test
    fun insert_withValidPosition_insertsPieceCorrectly() {
        val rope = Rope.create()
        rope.insert(0, "Hello".toByteArray())
        assertArrayEquals("Hello".toByteArray(), rope.get(0, 5))
    }

    @Test
    fun insert_withInvalidPosition_throwsException() {
        val rope = Rope.create()
        assertThrows(IllegalArgumentException::class.java) {
            rope.insert(-1, "Hello".toByteArray())
        }
    }

    @Test
    fun delete_() {
        val rope = Rope.buildRopeFromByteArray("".toByteArray())
        rope.insert(0, "a large text".toByteArray())
        rope.insert(8, "span of ".toByteArray())

        rope.delete(1, 6)

        assertEquals("a span of text", rope.toString())
    }

    @Test
    fun delete_withValidRange_deletesPieceCorrectly() {
        val rope = Rope.create()
        rope.insert(0, "Hello World".toByteArray())
        rope.delete(5, 6)
        assertArrayEquals("Hello".toByteArray(), rope.get(0, 5))
    }

    @Test
    fun delete_withMultipleLines_deletesCorrectly() {
        val rope = Rope.create()
        rope.insert(0,  "Hello\nWorld".toByteArray())
        rope.delete(5,  1)
        assertArrayEquals("HelloWorld".toByteArray(), rope.get(0, 10))
    }

    @Test
    fun get_withValidRange_returnsCorrectPiece() {
        val rope = Rope.create()
        rope.insert(0, "Hello World".toByteArray())
        assertArrayEquals("World".toByteArray(), rope.get(6, 5))
    }

    @Test
    fun get_withInvalidRange_returnsEmptyArray() {
        val rope = Rope.create()
        assertArrayEquals(ByteArray(0), rope.get(0, 5))
    }

    @Test
    fun length_returnsCorrectLength() {
        val rope = Rope.create()
        rope.insert(0, "Hello".toByteArray())
        kotlin.test.assertEquals(5, rope.length())
    }

    @Test
    fun last_row_issues_1() {
        // Arrange
        val rope = Rope.create()
        rope.insert(0, "1".toByteArray())
        rope.insert(1, "\n".toByteArray())
        rope.insert(2, "\n".toByteArray())
        rope.insert(3, "2".toByteArray())
        rope.insert(4, "3".toByteArray())


        // Act
        rope.delete(1, 1)
        val a = rope.get(0,2)
        val b = rope.get(2, 2)

        // Assert
        assertArrayEquals("1\n".toByteArray(), a)
        assertArrayEquals("23".toByteArray(), b)
    }

}