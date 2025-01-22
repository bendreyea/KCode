package core

import org.editor.core.PieceTable
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

class PieceTests {
    @Test
    fun insert_withValidPosition_insertsPieceCorrectly() {
        val pieceTable = PieceTable.create()
        pieceTable.insert(0, "Hello".toByteArray())
        assertArrayEquals("Hello".toByteArray(), pieceTable.get(0, 5))
    }

    @Test
    fun insert_withInvalidPosition_throwsException() {
        val pieceTable = PieceTable.create()
        assertThrows(IllegalArgumentException::class.java) {
            pieceTable.insert(-1, "Hello".toByteArray())
        }
    }

    @Test
    fun delete_() {
        val pt = PieceTable.create()
        pt.insert(0, "a large text".toByteArray())
        pt.insert(8, "span of ".toByteArray())

        pt.delete(1, 6)

        val bytes = pt.get(0, pt.length().toInt())
        Assertions.assertEquals("a span of text", String(bytes))
    }

    @Test
    fun delete_withValidRange_deletesPieceCorrectly() {
        val pieceTable = PieceTable.create()
        pieceTable.insert(0, "Hello World".toByteArray())
        pieceTable.delete(5, 6)
        assertArrayEquals("Hello".toByteArray(), pieceTable.get(0, 5))
    }

    @Test
    fun delete_withMultipleLines_deletesCorrectly() {
        val pieceTable = PieceTable.create()
        pieceTable.insert(0,  "Hello\nWorld".toByteArray())
        pieceTable.delete(5,  1)
        assertArrayEquals("HelloWorld".toByteArray(), pieceTable.get(0, 10))
    }

    @Test
    fun get_withValidRange_returnsCorrectPiece() {
        val pieceTable = PieceTable.create()
        pieceTable.insert(0, "Hello World".toByteArray())
        assertArrayEquals("World".toByteArray(), pieceTable.get(6, 5))
    }

    @Test
    fun get_withInvalidRange_returnsEmptyArray() {
        val pieceTable = PieceTable.create()
        assertArrayEquals(ByteArray(0), pieceTable.get(0, 5))
    }

    @Test
    fun length_returnsCorrectLength() {
        val pieceTable = PieceTable.create()
        pieceTable.insert(0, "Hello".toByteArray())
        assertEquals(5, pieceTable.length())
    }
}