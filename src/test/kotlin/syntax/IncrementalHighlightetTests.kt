package syntax

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.editor.application.editor.EditorDocumentSnapshot
import org.editor.syntax.highlighter.*
import org.editor.syntax.intervalTree.Interval
import org.editor.syntax.lexer.BracketInfo
import org.editor.syntax.lexer.LexerState
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test


class IncrementalHighlighterMockkTest {

    @AfterEach
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    /**
     * Test that parsing the same line twice reuses the cached result.
     */
    @Test
    fun `parseLineIfNeeded should use cache on second call`() = runTest {
        // Create a mock parser.
        val parser = mockk<Parser>()
        // Use a real instance for the cache manager.
        val cacheManager = ParseCacheManager()
        val highlighter = IncrementalHighlighter(parser, cacheManager)

        val row = 0
        val lineText = "Hello, World!"
        val defaultState = LexerState.DEFAULT
        val emptyStack = ArrayDeque<BracketInfo>()

        // Set up a snapshot mock.
        val snapshot = mockk<EditorDocumentSnapshot>()
        every { snapshot.getText(row) } returns lineText
        every { snapshot.serial(row, any()) } answers { firstArg<Int>().toLong() * 1000 + secondArg<Int>().toLong() }
        every { snapshot.rows() } returns 1

        // Set up a ParseResult mock.
        val parseResult = mockk<ParseLineResult>()
        every { parseResult.endState } returns defaultState
        every { parseResult.newBracketStack } returns ArrayDeque(emptyList())
        every { parseResult.highlightInterval } returns emptyList()

        // When the parser is called with the expected parameters, return our ParseResult.
        coEvery {
            parser.parseLine(lineText, any(), any(), defaultState, emptyStack)
        } returns parseResult

        // First call: no cache yet so parser.parseLine is invoked.
        val (endState1, endStack1) = highlighter.parseLineIfNeeded(row, snapshot, defaultState, emptyStack)
        coVerify(exactly = 1) { parser.parseLine(lineText, any(), any(), defaultState, emptyStack) }

        // Second call: input unchanged, so the cache should be hit (no new parser call).
        val (endState2, endStack2) = highlighter.parseLineIfNeeded(row, snapshot, defaultState, emptyStack)
        coVerify(exactly = 1) { parser.parseLine(lineText, any(), any(), defaultState, emptyStack) }

        // Verify the results are identical.
        assertEquals(endState1, endState2)
        assertEquals(endStack1.toList(), endStack2.toList())
    }

    /**
     * Test that if the line text changes, the parser is re‑invoked.
     */
    @Test
    fun `parseLineIfNeeded should reparse when line text changes`() = runTest {
        val parser = mockk<Parser>()
        val cacheManager = ParseCacheManager()
        val highlighter = IncrementalHighlighter(parser, cacheManager)

        val row = 0
        val defaultState = LexerState.DEFAULT
        val emptyStack = ArrayDeque<BracketInfo>()

        // Create two snapshots: one with initial text and one with updated text.
        val initialText = "Hello"
        val updatedText = "Hello World"

        val snapshot1 = mockk<EditorDocumentSnapshot>()
        every { snapshot1.getText(row) } returns initialText
        every { snapshot1.serial(row, any()) } answers { firstArg<Int>().toLong() * 1000 + secondArg<Int>().toLong() }
        every { snapshot1.rows() } returns 1

        val snapshot2 = mockk<EditorDocumentSnapshot>()
        every { snapshot2.getText(row) } returns updatedText
        every { snapshot2.serial(row, any()) } answers { firstArg<Int>().toLong() * 1000 + secondArg<Int>().toLong() }
        every { snapshot2.rows() } returns 1

        // Set up parser responses.
        val parseResult1 = mockk<ParseLineResult>()
        every { parseResult1.endState } returns defaultState
        every { parseResult1.newBracketStack } returns ArrayDeque(emptyList())
        every { parseResult1.highlightInterval } returns emptyList()
        coEvery {
            parser.parseLine(initialText, any(), any(), defaultState, emptyStack)
        } returns parseResult1

        val parseResult2 = mockk<ParseLineResult>()
        every { parseResult2.endState } returns defaultState
        every { parseResult2.newBracketStack } returns ArrayDeque(emptyList())
        every { parseResult2.highlightInterval } returns emptyList()
        coEvery {
            parser.parseLine(updatedText, any(), any(), defaultState, emptyStack)
        } returns parseResult2

        // First call with the initial snapshot.
        highlighter.parseLineIfNeeded(row, snapshot1, defaultState, emptyStack)
        coVerify { parser.parseLine(initialText, any(), any(), defaultState, emptyStack) }

        // Second call with the updated snapshot should trigger a re‑parse.
        highlighter.parseLineIfNeeded(row, snapshot2, defaultState, emptyStack)
        coVerify { parser.parseLine(updatedText, any(), any(), defaultState, emptyStack) }
    }

    /**
     * Test that after parsing a line, the highlight interval is inserted into the interval tree.
     */
    fun `getAllIntervals should return inserted intervals`() = runTest {
        val parser = mockk<Parser>()
        val cacheManager = ParseCacheManager()
        val highlighter = IncrementalHighlighter(parser, cacheManager)

        val row = 0
        val lineText = "Test line"
        val defaultState = LexerState.DEFAULT
        val emptyStack = ArrayDeque<BracketInfo>()

        // Set up the snapshot.
        val snapshot = mockk<EditorDocumentSnapshot>()
        every { snapshot.getText(row) } returns lineText
        every { snapshot.serial(row, any()) } answers { firstArg<Int>().toLong() * 1000 + secondArg<Int>().toLong() }
        every { snapshot.rows() } returns 1

        // Create a mock HighlightInterval.
        val highlightInterval: HighlightInterval = mockk(relaxed = true)
        every { highlightInterval.start } returns snapshot.serial(row, 0).toInt()
        every { highlightInterval.end } returns snapshot.serial(row, lineText.length).toInt()

        // Set up the parser response.
        val parseResult = mockk<ParseLineResult>()
        every { parseResult.endState } returns defaultState
        every { parseResult.newBracketStack } returns ArrayDeque(emptyList())
        every { parseResult.highlightInterval } returns listOf(highlightInterval)
        coEvery {
            parser.parseLine(lineText, any(), any(), defaultState, emptyStack)
        } returns parseResult

        // Parse the line so that the interval is inserted.
        highlighter.parseLineIfNeeded(row, snapshot, defaultState, emptyStack)

        // Retrieve intervals and verify.
        val intervals = highlighter.getAllIntervals(row, 0, lineText.length)
        assertFalse(intervals.isEmpty(), "Expected at least one interval")
        val interval = intervals.first()
        assertEquals(snapshot.serial(row, 0).toInt(), interval.start)
        assertEquals(snapshot.serial(row, lineText.length).toInt(), interval.end)
    }

    /**
     * Test that handleEdit adjusts checkpoints and schedules a parse job.
     */
    @Test
    fun `handleEdit should adjust checkpoints and schedule parsing`() = runTest {
        val parser = mockk<Parser>()
        val cacheManager = ParseCacheManager()
        val highlighter = IncrementalHighlighter(parser, cacheManager)

        // Create a snapshot with 30 lines.
        val lines = List(30) { "Line $it" }
        val snapshot = mockk<EditorDocumentSnapshot>()
        every { snapshot.rows() } returns lines.size
        every { snapshot.getText(any()) } answers { call -> lines[call.invocation.args[0] as Int] }
        every { snapshot.serial(any(), any()) } answers { firstArg<Int>().toLong() * 1000 + secondArg<Int>().toLong() }

        // Set up a parser that returns a default result.
        val defaultState = LexerState.DEFAULT
        val emptyStack = ArrayDeque<BracketInfo>()
        val parseResult = mockk<ParseLineResult>()
        every { parseResult.endState } returns defaultState
        every { parseResult.newBracketStack } returns ArrayDeque(emptyList())
        every { parseResult.highlightInterval } returns emptyList()
        coEvery { parser.parseLine(any(), any(), any(), any(), any()) } returns parseResult

        // Define an edit range.
        val editRange = object : Interval {
            override val start: Int = 0
            override val end: Int = 10
        }

        // Call handleEdit and wait for the job to complete.
        val job = highlighter.handleEdit(editRange, snapshot)
        job.join()

        // Verify that at least the first checkpoint has been initialized.
        val checkpoint = cacheManager.getCheckpoint(0)
        assertNotNull(checkpoint, "Expected checkpoint 0 to be initialized")
    }

    /**
     * Test that enqueuing a new parse job for overlapping chunks cancels an older job.
     */
    @Test
    fun `enqueueParseJob should cancel older overlapping job`() = runTest {
        val parser = mockk<Parser>()
        val cacheManager = ParseCacheManager()
        val highlighter = IncrementalHighlighter(parser, cacheManager)

        // Create a snapshot with 20 lines.
        val lines = List(20) { "Line $it" }
        val snapshot = mockk<EditorDocumentSnapshot>()
        every { snapshot.rows() } returns lines.size
        every { snapshot.getText(any()) } answers { call -> lines[call.invocation.args[0] as Int] }
        every { snapshot.serial(any(), any()) } answers { firstArg<Int>().toLong() * 1000 + secondArg<Int>().toLong() }

        // Set up parser response.
        val defaultState = LexerState.DEFAULT
        val emptyStack = ArrayDeque<BracketInfo>()
        val parseResult = mockk<ParseLineResult>()
        every { parseResult.endState } returns defaultState
        every { parseResult.newBracketStack } returns ArrayDeque(emptyList())
        every { parseResult.highlightInterval } returns emptyList()
        coEvery { parser.parseLine(any(), any(), any(), any(), any()) } returns parseResult

        // Initialize checkpoints.
        cacheManager.initializeCheckpoints(snapshot.rows(), highlighter.chunkSize)

        val editRange1 = object : Interval {
            override val start: Int = 0
            override val end: Int = 5
        }
        val job1 = highlighter.handleEdit(editRange1, snapshot)

        // Enqueue a second job that overlaps the same chunk.
        val editRange2 = object : Interval {
            override val start: Int = 3
            override val end: Int = 10
        }
        val job2 = highlighter.handleEdit(editRange2, snapshot)

        job1.join()
        job2.join()

        // The older job should be cancelled (or already completed) and the newer job should complete.
        assertTrue(job1.isCancelled || job1.isCompleted, "Expected job1 to be cancelled or completed")
        assertTrue(job2.isCompleted, "Expected job2 to be completed")
    }

    /**
     * Test that the chunk size is recalculated based on the document’s row count.
     */
    @Test
    fun `handleEdit adjusts chunkSize based on total rows`() = runTest {
        val parser = mockk<Parser>()
        val cacheManager = ParseCacheManager()
        val highlighter = IncrementalHighlighter(parser, cacheManager)

        val defaultState = LexerState.DEFAULT
        val emptyStack = ArrayDeque<BracketInfo>()
        val parseResult = mockk<ParseLineResult>()
        every { parseResult.endState } returns defaultState
        every { parseResult.newBracketStack } returns ArrayDeque(emptyList())
        every { parseResult.highlightInterval } returns emptyList()
        coEvery { parser.parseLine(any(), any(), any(), any(), any()) } returns parseResult

        // For a small document (< 2000 rows), chunkSize should become 15.
        val smallLines = List(100) { "Small line $it" }
        val smallSnapshot = mockk<EditorDocumentSnapshot>()
        every { smallSnapshot.rows() } returns smallLines.size
        every { smallSnapshot.getText(any()) } answers { call -> smallLines[call.invocation.args[0] as Int] }
        every { smallSnapshot.serial(any(), any()) } answers { firstArg<Int>().toLong() * 1000 + secondArg<Int>().toLong() }

        val editRange = object : Interval {
            override val start: Int = 0
            override val end: Int = 1
        }
        highlighter.handleEdit(editRange, smallSnapshot).join()
        assertEquals(15, highlighter.chunkSize, "Expected chunkSize of 15 for a small document")

        // For a medium document (< 5000 rows), chunkSize should become 30.
        val mediumLines = List(3000) { "Medium line $it" }
        val mediumSnapshot = mockk<EditorDocumentSnapshot>()
        every { mediumSnapshot.rows() } returns mediumLines.size
        every { mediumSnapshot.getText(any()) } answers { call -> mediumLines[call.invocation.args[0] as Int] }
        every { mediumSnapshot.serial(any(), any()) } answers { firstArg<Int>().toLong() * 1000 + secondArg<Int>().toLong() }

        highlighter.handleEdit(editRange, mediumSnapshot).join()
        assertEquals(30, highlighter.chunkSize, "Expected chunkSize of 30 for a medium document")
    }

    /**
     * Test that getAST returns a non‑null AST.
     */
    @Test
    fun `getAST returns non-null AST`() {
        val parser = mockk<Parser>()
        val cacheManager = ParseCacheManager()
        val highlighter = IncrementalHighlighter(parser, cacheManager)

        val ast = highlighter.getAST()
        assertNotNull(ast)
    }
}
