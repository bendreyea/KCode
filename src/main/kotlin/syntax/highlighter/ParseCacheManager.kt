package org.editor.syntax.highlighter

import org.editor.syntax.lexer.BracketInfo
import org.editor.syntax.lexer.LexerState
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val lineCaches = object : LinkedHashMap<Int, LineCache>(1000, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, LineCache>): Boolean {
        return size > 1000
    }
}

data class LineCache(
    val hash: Int,
    val length: Int,
    val startState: LexerState,
    val startBracketStack: List<BracketInfo>,
    val endState: LexerState,
    val endBracketStack: List<BracketInfo>
)

data class IncrementalParseCheckpoint(
    val chunkStartLine: Int,            // e.g. 0, 100, 200, ...
    var startLexerState: LexerState,    // how we begin parsing at this chunk
    val startBracketStack: ArrayDeque<BracketInfo>
)

class ParseCacheManager {
    private val lineCaches = ConcurrentHashMap<Int, LineCache>()
    private val checkpoints = mutableListOf<IncrementalParseCheckpoint>()
    private val checkpointLock = ReentrantLock()  // lock for checkpoint operations

    fun getLineCache(row: Int): LineCache? = lineCaches[row]

    fun updateLineCache(row: Int, cache: LineCache) {
        lineCaches[row] = cache
    }

    fun initializeCheckpoints(totalRows: Int, chunkSize: Int) {
        checkpointLock.withLock {
            checkpoints.clear()
            val numChunks = (totalRows + chunkSize - 1) / chunkSize
            for (i in 0 until numChunks) {
                checkpoints.add(
                    IncrementalParseCheckpoint(
                        chunkStartLine = i * chunkSize,
                        startLexerState = LexerState.DEFAULT,
                        startBracketStack = ArrayDeque()
                    )
                )
            }
        }
    }

    fun resizeCheckpoints(totalRows: Int, chunkSize: Int) {
        checkpointLock.withLock {
            val oldNumChunks = checkpoints.size
            val newNumChunks = (totalRows + chunkSize - 1) / chunkSize
            if (newNumChunks > oldNumChunks) {
                for (i in oldNumChunks until newNumChunks) {
                    val startLine = i * chunkSize
                    checkpoints.add(
                        IncrementalParseCheckpoint(
                            chunkStartLine = startLine,
                            startLexerState = LexerState.DEFAULT,
                            startBracketStack = ArrayDeque()
                        )
                    )
                }
            } else if (newNumChunks < oldNumChunks) {
                // Remove extra checkpoints.
                checkpoints.subList(newNumChunks, checkpoints.size).clear()
            }
        }
    }

    fun getCheckpoint(index: Int): IncrementalParseCheckpoint? = checkpoints.getOrNull(index)

    fun updateCheckpoint(index: Int, newState: LexerState, newStack: ArrayDeque<BracketInfo>) {
        checkpointLock.withLock {
            checkpoints.getOrNull(index)?.let { checkpoint ->
                checkpoint.startLexerState = newState
                checkpoint.startBracketStack.clear()
                checkpoint.startBracketStack.addAll(newStack)
            }
        }
    }
}
