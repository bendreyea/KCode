package org.editor.syntax.highlighter

import kotlinx.coroutines.*
import org.editor.ApplicationScope
import org.editor.application.editor.EditorDocumentSnapshot
import org.editor.syntax.intervalTree.Interval
import org.editor.syntax.intervalTree.IntervalTree
import org.editor.syntax.lexer.BracketInfo
import org.editor.syntax.lexer.LexerState
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.collections.ArrayDeque
import kotlin.concurrent.withLock
import kotlin.coroutines.coroutineContext

data class ParseJob(
    val version: Int,
    val chunkIndices: Set<Int>,
    val snapshot: EditorDocumentSnapshot,
    val job: Job
) : Comparable<ParseJob> {
    override fun compareTo(other: ParseJob): Int = other.version - version
}

class IncrementalHighlighter(
    private val parser: Parser,
    private val cacheManager: ParseCacheManager
) {
    private val parsingQueue = PriorityQueue<ParseJob>(compareByDescending { it.version })
    private var currentJobVersion = 0
    private val queueLock = ReentrantLock()
    private val intervalLock = ReentrantReadWriteLock()
    private val intervals = ConcurrentHashMap<Int, List<HighlightInterval>>()
    private val intervalsTree = IntervalTree<HighlightInterval>()

    companion object {
        private const val DEFAULT_CHUNK_SIZE = 100
    }

    // Dynamically adjusted chunk size.
    var chunkSize = DEFAULT_CHUNK_SIZE

    // Global AST is maintained via an atomic reference.
    private val astRef = AtomicReference<ASTNode>(BlockNode(0, 0, emptyList(), null, 0))

    /**
     * Parses one line if needed by comparing the hash and the incoming starting state/stack with a cached value.
     */
    fun parseLineIfNeeded(
        row: Int,
        snapshot: EditorDocumentSnapshot,
        incomingState: LexerState,
        incomingBracketStack: ArrayDeque<BracketInfo>
    ): Pair<LexerState, ArrayDeque<BracketInfo>> {
        val lineText = snapshot.getText(row)
        val newHash = lineText.hashCode()
        val newLength = lineText.length
        val cached = cacheManager.getLineCache(row)
        // Check that the line text is unchanged and that the state we use to parse it is the same.
        if (cached != null &&
            cached.hash == newHash &&
            incomingState == cached.startState &&
            incomingBracketStack.toList() == cached.startBracketStack
        ) {
            // Skip re‑parsing if nothing has changed.
            return Pair(cached.endState, ArrayDeque(cached.endBracketStack))
        }

        // calculate the offset of the line
        val start = snapshot.serial(row, 0);
        val end = snapshot.serial(row, lineText.length);
        val parseResult = parser.parseLine(lineText, start, end, incomingState, incomingBracketStack)
        intervalLock.writeLock().withLock {
            parseResult.highlightInterval.forEach(intervalsTree::insert)
        }

        // Cache both the starting and final state for this line.
        cacheManager.updateLineCache(row, LineCache(
            hash = newHash,
            length = newLength,
            startState = incomingState,
            startBracketStack = incomingBracketStack.toList(),
            endState = parseResult.endState,
            endBracketStack = parseResult.newBracketStack.toList()
        )
        )

        // Atomically merge the new AST result.
        // astRef.getAndUpdate { currentAst -> currentAst.mergeWith(parseResult.ast) }
        return Pair(parseResult.endState, ArrayDeque(parseResult.newBracketStack))
    }

    fun getAllIntervals(row: Int, begin: Int, end: Int): List<HighlightInterval> {
        intervalLock.readLock().withLock {
            return intervalsTree.queryOverlapping(row + begin, row + end)
        }
    }

    /**
     * Re‑parses a chunk starting at a given checkpoint.
     */
    private fun parseChunkIfNeeded(chunkIndex: Int, snapshot: EditorDocumentSnapshot) {
        try {
            val checkpoint = cacheManager.getCheckpoint(chunkIndex) ?: return
            var currentState = checkpoint.startLexerState
            val bracketStack = ArrayDeque(checkpoint.startBracketStack)
            val lastLineInChunk = checkpoint.chunkStartLine + chunkSize - 1
            val totalRows = snapshot.rows()
            for (row in checkpoint.chunkStartLine..minOf(lastLineInChunk, totalRows - 1)) {
                val (newState, newStack) = parseLineIfNeeded(row, snapshot, currentState, bracketStack)
                currentState = newState
                bracketStack.clear()
                bracketStack.addAll(newStack)
            }

            // Update the checkpoint with the final state after parsing this chunk.
            cacheManager.updateCheckpoint(chunkIndex, currentState, bracketStack)
        }
        catch (e: Exception) {
            println("Error parsing chunk $chunkIndex: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Adjusts the chunk size dynamically and re‑initializes checkpoints if needed.
     */
    private fun resizeCheckpoints(newTotalRows: Int) {
        val oldChunkSize = chunkSize
        recalcChunkSize(newTotalRows)
        if (chunkSize != oldChunkSize) {
            cacheManager.initializeCheckpoints(newTotalRows, chunkSize)
        } else {
            cacheManager.resizeCheckpoints(newTotalRows, chunkSize)
        }
    }

    /**
     * Called when the document is edited.
     */
    fun handleEdit(editRange: Interval, updatedText: EditorDocumentSnapshot) {
        resizeCheckpoints(updatedText.rows())

        val firstChunkIndex = (editRange.start) / chunkSize
        val lastChunkIndex = (editRange.end) / chunkSize
        val affectedChunks = (firstChunkIndex..lastChunkIndex).toSet()

        enqueueParseJob(affectedChunks, updatedText)
    }

    // provide getters for the AST or highlight intervals.
    fun getAST(): ASTNode = astRef.get()

    private fun enqueueParseJob(chunkIndices: Set<Int>, snapshot: EditorDocumentSnapshot): Job {
        queueLock.withLock {
            currentJobVersion++
            val newJob = ParseJob(
                version = currentJobVersion,
                chunkIndices = chunkIndices,
                snapshot = snapshot,
                job = ApplicationScope.scope.launch(Dispatchers.Default) {
                    processChunks(chunkIndices, snapshot, currentJobVersion)
                }
            )

            // Cancel older jobs that overlap the same chunks
            parsingQueue.removeIf { existing ->
                if (existing.chunkIndices.any { it in chunkIndices }) {
                    existing.job.cancel()
                    true
                } else false
            }

            parsingQueue.add(newJob)
            return newJob.job
        }
    }

    private suspend fun processChunks(
        chunkIndices: Set<Int>,
        snapshot: EditorDocumentSnapshot,
        jobVersion: Int
    ) {
        chunkIndices.forEach { chunkIndex ->
            if (!coroutineContext.isActive)
                return@forEach

            parseChunkIfNeeded(chunkIndex, snapshot)

            queueLock.withLock {
                if (jobVersion < currentJobVersion) {
                    parsingQueue.removeIf { it.version == jobVersion }
                    coroutineContext.cancel()
                }
            }

            yield()
        }
    }

    /**
     * Dynamically recalculates the chunk size based on the total number of rows.
     */
    private fun recalcChunkSize(totalRows: Int) {
        chunkSize = when {
            totalRows < 2000 -> 15
            totalRows < 5000 -> 30
            totalRows < 10000 -> 60
            totalRows < 50000 -> 90
            else -> 120
        }
    }
}