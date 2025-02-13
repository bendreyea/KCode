package org.editor.syntax.highlighter

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.editor.ApplicationScope
import org.editor.application.editor.EditorDocumentSnapshot
import org.editor.syntax.intervalTree.Interval
import org.editor.syntax.intervalTree.IntervalTree
import org.editor.syntax.lexer.BracketInfo
import org.editor.syntax.lexer.LexerState
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.ArrayDeque
import kotlin.coroutines.coroutineContext
import kotlin.math.min

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
    // A priority queue to schedule parse jobs (newer jobs come first).
    private val parsingQueue = PriorityQueue<ParseJob>()
    private val currentJobVersion = AtomicInteger(0)

    // Replace ReentrantLock with Kotlin’s Mutex (non‑blocking).
    private val queueMutex = Mutex()
    // Use a Mutex instead of a ReadWriteLock to protect the interval tree.
    private val intervalMutex = Mutex()

    // A shared interval tree containing all highlight intervals.
    private val intervalsTree = IntervalTree<HighlightInterval>()
    private val highlightIntervalsByLine = ConcurrentHashMap<Int, List<HighlightInterval>>()

    companion object {
        private const val DEFAULT_CHUNK_SIZE = 100
    }

    // Dynamically adjusted chunk size.
    var chunkSize: Int = DEFAULT_CHUNK_SIZE
        private set

    // Global AST maintained in an atomic reference.
    private val astRef = AtomicReference<ASTNode>(BlockNode(0, 0, emptyList(), null, 0))

    /**
     * Incrementally parse one line if needed (using the cache to avoid re‑parsing unchanged lines).
     * Updates the interval tree with new highlight intervals.
     */
    suspend fun parseLineIfNeeded(
        row: Int,
        snapshot: EditorDocumentSnapshot,
        incomingState: LexerState,
        incomingBracketStack: ArrayDeque<BracketInfo>
    ): Pair<LexerState, ArrayDeque<BracketInfo>> {
        val lineText = snapshot.getText(row)
        val newHash = lineText.hashCode()
        val newLength = lineText.length
        val cached = cacheManager.getLineCache(row)

        // Skip re‑parsing if the line text and parser input (state/stack) are unchanged.
        if (cached != null &&
            cached.hash == newHash &&
            incomingState == cached.startState &&
            incomingBracketStack.toList() == cached.startBracketStack
        ) {
            return Pair(cached.endState, ArrayDeque(cached.endBracketStack))
        }

        // Calculate the line’s offset.
//        val start = snapshot.serial(row, 0)
//        val end = snapshot.serial(row, lineText.length)
        val parseResult = parser.parseLine(lineText, 0, 0, incomingState, incomingBracketStack)


//        // Update the interval tree
//        intervalMutex.withLock {
//            // Calculate the absolute positions for the line.
//            val lineStart = snapshot.serial(row, 0).toInt()
//            val lineEnd = snapshot.serial(row, lineText.length).toInt()
//            // Query and delete all existing intervals that overlap this line.
//            val existingIntervals = intervalsTree.queryOverlapping(lineStart, lineEnd)
//            existingIntervals.forEach { intervalsTree.delete(it) }
//
//            // Insert the new intervals.
//            parseResult.highlightInterval.forEach { intervalsTree.insert(it) }
//        }

        highlightIntervalsByLine[row] = parseResult.highlightInterval


        // Update the cache for this line.
        cacheManager.updateLineCache(
            row, LineCache(
                hash = newHash,
                length = newLength,
                startState = incomingState,
                startBracketStack = incomingBracketStack.toList(),
                endState = parseResult.endState,
                endBracketStack = parseResult.newBracketStack.toList()
            )
        )

        return Pair(parseResult.endState, ArrayDeque(parseResult.newBracketStack))
    }


    fun getLineHighlightIntervals(row: Int): List<HighlightInterval> {
        return highlightIntervalsByLine[row] ?: emptyList()
    }


    /**
     * Retrieve highlight intervals overlapping the given range.
     */
    suspend fun getAllIntervals(row: Int, begin: Int, end: Int): List<HighlightInterval> =
        intervalMutex.withLock { intervalsTree.queryOverlapping(row + begin, row + end) }

    /**
     * Parse a chunk of lines (from the checkpoint) if needed.
     */
    private suspend fun parseChunkIfNeeded(chunkIndex: Int, snapshot: EditorDocumentSnapshot) {
        try {
            val checkpoint = cacheManager.getCheckpoint(chunkIndex) ?: return
            var currentState = checkpoint.startLexerState
            val bracketStack = ArrayDeque(checkpoint.startBracketStack)
            val lastLineInChunk = min(checkpoint.chunkStartLine + chunkSize - 1, snapshot.rows() - 1)

            for (row in checkpoint.chunkStartLine..lastLineInChunk) {
                val (newState, newStack) = parseLineIfNeeded(row, snapshot, currentState, bracketStack)
                currentState = newState
                bracketStack.clear()
                bracketStack.addAll(newStack)
            }

            // Update the checkpoint with the final parser state for the chunk.
            cacheManager.updateCheckpoint(chunkIndex, currentState, bracketStack)
        } catch (e: Exception) {
            println("Error parsing chunk $chunkIndex: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Recalculate the chunk size (and re‑initialize checkpoints if needed) based on the total rows.
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

    /**
     * When the document is edited, update checkpoints and schedule re‑parsing of affected chunks.
     */
    suspend fun handleEdit(editRange: Interval, updatedSnapshot: EditorDocumentSnapshot): Job {
        // Adjust checkpoints if the number of rows has changed.
        val text = updatedSnapshot.getText(0)
        resizeCheckpoints(updatedSnapshot.rows())

        val firstChunkIndex = editRange.start / chunkSize
        val lastChunkIndex = editRange.end / chunkSize
        val affectedChunks = (firstChunkIndex..lastChunkIndex).toSet()

        return enqueueParseJob(affectedChunks, updatedSnapshot)
    }

    /**
     * Enqueue a new parse job for the given set of chunks.
     * Any older jobs that overlap these chunks are cancelled.
     */
    private suspend fun enqueueParseJob(chunkIndices: Set<Int>, snapshot: EditorDocumentSnapshot): Job {
        return queueMutex.withLock {
            val newVersion = currentJobVersion.incrementAndGet()
            val newJob = ParseJob(
                version = newVersion,
                chunkIndices = chunkIndices,
                snapshot = snapshot,
                job = ApplicationScope.scope.launch {
                    processChunks(chunkIndices, snapshot, newVersion)
                }
            )

            // Cancel older jobs that overlap these chunks.
            parsingQueue.removeIf { existing ->
                if (existing.chunkIndices.any { it in chunkIndices }) {
                    existing.job.cancel()
                    true
                } else false
            }

            parsingQueue.add(newJob)
            newJob.job
        }
    }

    /**
     * Process (parse) each affected chunk sequentially.
     * If a newer job has started meanwhile, cancel the current work.
     */
    private suspend fun processChunks(chunkIndices: Set<Int>, snapshot: EditorDocumentSnapshot, jobVersion: Int) {
        for (chunkIndex in chunkIndices) {
            if (!coroutineContext.isActive) return

            parseChunkIfNeeded(chunkIndex, snapshot)

            queueMutex.withLock {
                if (jobVersion < currentJobVersion.get()) {
                    parsingQueue.removeIf { it.version == jobVersion }
                    coroutineContext.cancel()  // Cancel this job since a newer one exists.
                }
            }

            yield()
        }
    }

    /**
     * Adjust (or re‑initialize) checkpoints when the document’s row count changes.
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

    // Optional: Expose the current AST (if used) in a thread‑safe way.
    fun getAST(): ASTNode = astRef.get()
}