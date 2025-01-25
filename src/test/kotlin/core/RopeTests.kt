package core

import org.editor.core.Rope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RopeTests {

    @Test
    fun testBuildRopeFromString() {
        val rope = Rope.buildRopeFromByteArray("Hello, Rope!".toByteArray())
        assertEquals(12, rope.length())
        assertEquals("Hello, Rope!", rope.toString())
    }

    @Test
    fun testConcat() {
        val rope1 = Rope.buildRopeFromByteArray("Hello".toByteArray())
        val rope2 = Rope.buildRopeFromByteArray(", Rope!".toByteArray())
        val concatenated = rope1.concat(rope2)

        assertEquals(12, concatenated.length())
        assertEquals("Hello, Rope!", concatenated.toString())
    }

    @Test
    fun testSubstring() {
        val rope = Rope.buildRopeFromByteArray("Hello, Rope!".toByteArray())
        val sub = rope.substring(7, 11)  // "Rope"

        assertEquals(4, sub.length())
        assertEquals("Rope", sub.toString())
    }

    @Test
    fun testInsert() {
        val rope = Rope.buildRopeFromByteArray("Hello, World!".toByteArray())
        rope.insert(7, "Beautiful ".toByteArray())
        assertEquals("Hello, Beautiful World!", rope.toString())
    }

    @Test
    fun testDelete() {
        val rope = Rope.buildRopeFromByteArray("Hello, Cruel World!".toByteArray())
        rope.delete(7, 13)  // remove "Cruel "
        assertEquals("Hello, World!", rope.toString())
    }

    @Test
    fun testComplexOperations() {
        val rope1 = Rope.buildRopeFromByteArray("Hello".toByteArray())
        val rope2 = Rope.buildRopeFromByteArray(", Rope!".toByteArray())
        val rope3 = Rope.buildRopeFromByteArray(" Another String".toByteArray())

        // Concat them
        val combined = rope1.concat(rope2).concat(rope3)
        assertEquals("Hello, Rope! Another String", combined.toString())

        // Substring
        val sub = combined.substring(7, 12) // "Rope!"
        assertEquals("Rope!", sub.toString())

        // Insert
        combined.insert(0, ">>>".toByteArray())
        assertEquals(">>>Hello, Rope! Another String", combined.toString())

        // Delete
        combined.delete(3, 8) // remove "Hello"
        assertEquals(">>>, Rope! Another String", combined.toString())
    }
}