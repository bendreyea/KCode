package syntax

import org.editor.syntax.highlighter.LineCache
import org.editor.syntax.highlighter.ParseCacheManager
import org.editor.syntax.lexer.BracketInfo
import org.editor.syntax.lexer.LexerState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ParseCacheManagerTests {

    @Test
    fun testGetAndUpdateLineCache() {
        val manager = ParseCacheManager()
        val row = 5

        // Initially there should be no cache for the row.
        assertNull(manager.getLineCache(row))

        // Create a dummy LineCache.
        val dummyCache = LineCache(
            hash = 42,
            length = 10,
            startState = LexerState.DEFAULT,
            startBracketStack = emptyList(),
            endState = LexerState.DEFAULT,
            endBracketStack = emptyList()
        )

        manager.updateLineCache(row, dummyCache)
        val retrievedCache = manager.getLineCache(row)
        assertNotNull(retrievedCache)
        assertEquals(dummyCache, retrievedCache)
    }

    @Test
    fun testInitializeCheckpoints() {
        val manager = ParseCacheManager()
        val totalRows = 250
        val chunkSize = 100
        manager.initializeCheckpoints(totalRows, chunkSize)

        // Expect 3 checkpoints: lines 0, 100, and 200.
        val cp0 = manager.getCheckpoint(0)
        val cp1 = manager.getCheckpoint(1)
        val cp2 = manager.getCheckpoint(2)
        val cp3 = manager.getCheckpoint(3) // should be null

        assertNotNull(cp0)
        assertNotNull(cp1)
        assertNotNull(cp2)
        assertNull(cp3)

        assertEquals(0, cp0?.chunkStartLine)
        assertEquals(100, cp1?.chunkStartLine)
        assertEquals(200, cp2?.chunkStartLine)

        // Verify that each checkpoint starts with the default lexer state and an empty bracket stack.
        listOf(cp0, cp1, cp2).forEach { cp ->
            assertEquals(LexerState.DEFAULT, cp?.startLexerState)
            assertTrue(cp?.startBracketStack?.isEmpty() ?: false)
        }
    }

    @Test
    fun testResizeCheckpointsIncrease() {
        val manager = ParseCacheManager()
        val initialTotalRows = 150  // 2 chunks: 0 and 100.
        val chunkSize = 100
        manager.initializeCheckpoints(initialTotalRows, chunkSize)

        // Verify initial state.
        assertNotNull(manager.getCheckpoint(0))
        assertNotNull(manager.getCheckpoint(1))
        assertNull(manager.getCheckpoint(2))

        // Increase total rows to 250; now there should be 3 chunks: 0, 100, and 200.
        manager.resizeCheckpoints(totalRows = 250, chunkSize = chunkSize)
        val cp0 = manager.getCheckpoint(0)
        val cp1 = manager.getCheckpoint(1)
        val cp2 = manager.getCheckpoint(2)

        assertNotNull(cp0)
        assertNotNull(cp1)
        assertNotNull(cp2)
        assertEquals(0, cp0?.chunkStartLine)
        assertEquals(100, cp1?.chunkStartLine)
        assertEquals(200, cp2?.chunkStartLine)
    }

    @Test
    fun testResizeCheckpointsDecrease() {
        val manager = ParseCacheManager()
        val initialTotalRows = 250  // 3 chunks: 0, 100, 200.
        val chunkSize = 100
        manager.initializeCheckpoints(initialTotalRows, chunkSize)

        // Verify initial state.
        assertNotNull(manager.getCheckpoint(0))
        assertNotNull(manager.getCheckpoint(1))
        assertNotNull(manager.getCheckpoint(2))

        // Decrease total rows to 150; now only 2 chunks should remain: 0 and 100.
        manager.resizeCheckpoints(totalRows = 150, chunkSize = chunkSize)
        assertNotNull(manager.getCheckpoint(0))
        assertNotNull(manager.getCheckpoint(1))
        assertNull(manager.getCheckpoint(2))
        assertEquals(0, manager.getCheckpoint(0)?.chunkStartLine)
        assertEquals(100, manager.getCheckpoint(1)?.chunkStartLine)
    }

    @Test
    fun testGetCheckpoint() {
        val manager = ParseCacheManager()
        val totalRows = 150
        val chunkSize = 100
        manager.initializeCheckpoints(totalRows, chunkSize)

        val cp0 = manager.getCheckpoint(0)
        val cp1 = manager.getCheckpoint(1)
        val cp2 = manager.getCheckpoint(2) // out-of-range

        assertNotNull(cp0)
        assertNotNull(cp1)
        assertNull(cp2)
    }

    @Test
    fun testUpdateCheckpoint() {
        val manager = ParseCacheManager()
        val totalRows = 150
        val chunkSize = 100
        manager.initializeCheckpoints(totalRows, chunkSize)

        val newState = LexerState.DEFAULT
        val dummyBracket = BracketInfo('(', 0, 0, 0)
        val newStack = ArrayDeque<BracketInfo>().apply { add(dummyBracket) }

        // Update the first checkpoint.
        manager.updateCheckpoint(0, newState, newStack)
        val cp0 = manager.getCheckpoint(0)
        assertNotNull(cp0)
        assertEquals(newState, cp0?.startLexerState)
        assertEquals(1, cp0?.startBracketStack?.size)
        assertEquals(dummyBracket, cp0?.startBracketStack?.first())

        // Test that updating a non-existent checkpoint does not throw an exception.
        manager.updateCheckpoint(10, newState, newStack)
    }
}
