package core

import org.editor.core.Rope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RopeTests {

    @Test
    fun testBuildRopeFromString() {
        val rope = Rope.buildRopeFromString("Hello, Rope!")
        assertEquals(12, rope.length())
        assertEquals("Hello, Rope!", rope.toString())
    }

    @Test
    fun testConcat() {
        val rope1 = Rope.buildRopeFromString("Hello")
        val rope2 = Rope.buildRopeFromString(", Rope!")
        val concatenated = rope1.concat(rope2)

        assertEquals(12, concatenated.length())
        assertEquals("Hello, Rope!", concatenated.toString())
    }

    @Test
    fun testSubstring() {
        val rope = Rope.buildRopeFromString("Hello, Rope!")
        val sub = rope.substring(7, 11)  // "Rope"

        assertEquals(4, sub.length())
        assertEquals("Rope", sub.toString())
    }

    @Test
    fun testInsert() {
        val rope = Rope.buildRopeFromString("Hello, World!")
        rope.insert(7, "Beautiful ")
        assertEquals("Hello, Beautiful World!", rope.toString())
    }

    @Test
    fun testDelete() {
        val rope = Rope.buildRopeFromString("Hello, Cruel World!")
        rope.delete(7, 13)  // remove "Cruel "
        assertEquals("Hello, World!", rope.toString())
    }

    @Test
    fun testComplexOperations() {
        val rope1 = Rope.buildRopeFromString("Hello")
        val rope2 = Rope.buildRopeFromString(", Rope!")
        val rope3 = Rope.buildRopeFromString(" Another String")

        // Concat them
        val combined = rope1.concat(rope2).concat(rope3)
        assertEquals("Hello, Rope! Another String", combined.toString())

        // Substring
        val sub = combined.substring(7, 12) // "Rope!"
        assertEquals("Rope!", sub.toString())

        // Insert
        combined.insert(0, ">>>")
        assertEquals(">>>Hello, Rope! Another String", combined.toString())

        // Delete
        combined.delete(3, 8) // remove "Hello"
        assertEquals(">>>, Rope! Another String", combined.toString())
    }
}
