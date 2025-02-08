package org.editor.syntax

import org.editor.syntax.intervalTree.Interval
import org.editor.syntax.intervalTree.IntervalTree
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

sealed interface ASTNode : Interval {
    val children: List<ASTNode>
}

data class BlockNode(
    override val start: Int,
    override val end: Int,
    override val children: List<ASTNode>
) : ASTNode

data class TokenNode(
    val token: Token,
    override val start: Int,
    override val end: Int
) : ASTNode {
    override val children: List<ASTNode> = emptyList()
}

class IncrementalParser(private var text: CharSequence) {

    companion object {
        private const val DEFAULT_CHUNK_SIZE = 100
    }

    /** Number of lines per chunk. Adjusted dynamically. */
    var chunkSize = DEFAULT_CHUNK_SIZE

    // An interval tree for fast queries on the AST.
    private val intervalTree = IntervalTree<ASTNode>()
    private val astRef = AtomicReference<ASTNode>()

    private var lineStartOffsets: List<Int> = emptyList()
    private val chunkASTs = ConcurrentHashMap<Int, ASTNode>()
    private var chunkBoundaries: List<Pair<Int, Int>>

    init {
        // Precompute line starts and chunk boundaries.
        lineStartOffsets = computeLineStartOffsets(text)
        chunkBoundaries = computeChunkBoundaries(text, chunkSize)

        // Parse each chunk synchronously. (You could parse off‑screen chunks in background threads.)
        for ((index, boundary) in chunkBoundaries.withIndex()) {
            val (start, end) = boundary
            val ast = parseChunk(text, start, end)
            chunkASTs[index] = ast
        }

        // Merge the chunk ASTs into a global AST.
        updateGlobalAST()

        // Build the interval tree from the global AST.
        buildIntervalTree(astRef.get())
    }

    fun getAllIntervals(row: Int): List<Interval> {
        if (row < 0 || row >= lineStartOffsets.size) return emptyList()
        val startOffset = lineStartOffsets[row]
        val endOffset = if (row + 1 < lineStartOffsets.size) lineStartOffsets[row + 1] else text.length
        val result = mutableListOf<Interval>()
        result.addAll(intervalTree.queryOverlapping(startOffset, endOffset))
        return result
    }

    fun handleEdit(editRange: Interval, newText: String) {
        // Update the text.
        text = buildString {
            append(text.substring(0, editRange.start))
            append(newText)
            append(text.substring(editRange.end))
        }

        // Recompute line start offsets and chunk boundaries.
        lineStartOffsets = computeLineStartOffsets(text)
        chunkBoundaries = computeChunkBoundaries(text, chunkSize)

        // For production, re-parse only the affected chunks.
        // Here, for simplicity, we re-parse the whole document.
        chunkASTs.clear()
        for ((index, boundary) in chunkBoundaries.withIndex()) {
            val (start, end) = boundary
            val ast = parseChunk(text, start, end)
            chunkASTs[index] = ast
        }
        updateGlobalAST()

        // Rebuild the interval tree.
        intervalTree.clear()
        buildIntervalTree(astRef.get())
    }

    /**
     * Computes line start offsets so that given a row number you can quickly compute
     * the corresponding text offset range.
     */
    private fun computeLineStartOffsets(text: CharSequence): List<Int> {
        val offsets = mutableListOf(0)
        text.forEachIndexed { index, c ->
            if (c == '\n') {
                offsets.add(index + 1)
            }
        }
        return offsets
    }

    private fun updateGlobalAST() {
        val sortedKeys = chunkASTs.keys.toList().sorted()
        val children = sortedKeys.mapNotNull { chunkASTs[it] }
        val newGlobalAST = BlockNode(0, text.length, children)
        val merged = mergeAST(astRef.get(), newGlobalAST)
        astRef.set(merged)
    }

    /**
     * Recursively builds the interval tree from the AST.
     */
    private fun buildIntervalTree(node: ASTNode) {
        intervalTree.insert(node)
        node.children.forEach { buildIntervalTree(it) }
    }

    private fun buildInitialAST() {
        val lexer = Lexer(text)
        val tokens = lexer.tokenize(0, text.length)
        val newAST = parseTokens(tokens, text.length)
        astRef.set(newAST)
    }

    private fun initializeCheckpoints(totalRows: Int) {
        recalcChunkSize(totalRows)
        val numChunks = (totalRows + chunkSize - 1) / chunkSize
    }

    /**
     * Dynamically resizes (and possibly re-initializes) parse checkpoints if the total line count
     * has changed or if we want to recalc chunkSize for performance reasons.
     */
    fun resizeCheckpoints(newTotalRows: Int) {
        // Possibly adjust chunkSize based on new total row count
        val oldChunkSize = chunkSize
        recalcChunkSize(newTotalRows)
        val chunkSizeChanged = (chunkSize != oldChunkSize)

        val oldNumChunks = 1
        val newNumChunks = (newTotalRows + chunkSize - 1) / chunkSize

        if (chunkSizeChanged) {
            initializeCheckpoints(newTotalRows)
            return
        }

        // If chunk size hasn't changed, we can partially grow or shrink parseCheckpoints.
        if (newNumChunks == oldNumChunks) {
            // No checkpoint changes needed
            return
        } else if (newNumChunks > oldNumChunks) {
            // Need more parse checkpoints at the end
            for (i in oldNumChunks until newNumChunks) {
                val startLine = i * chunkSize

            }
        } else {
            // We have too many parse checkpoints; remove the extras
        }
    }

    /** Decide a new chunk size based on total lines. */
    private fun recalcChunkSize(totalRows: Int) {
        chunkSize = when {
            totalRows < 2000 -> 15
            totalRows < 5000 -> 30
            totalRows < 10000 -> 60
            totalRows < 50000 -> 90
            else -> 120
        }
    }

    private fun parseChunk(text: CharSequence, start: Int, end: Int): ASTNode {
        val lexer = Lexer(text)
        val tokens = lexer.tokenize(start, end)
        return parseTokens(tokens, text.length)
    }

    private fun computeChunkBoundaries(text: CharSequence, linesPerChunk: Int): List<Pair<Int, Int>> {
        val boundaries = mutableListOf<Pair<Int, Int>>()
        var currentOffset = 0
        var lineCount = 0
        var chunkStart = 0
        while (currentOffset < text.length) {
            if (text[currentOffset] == '\n') {
                lineCount++
                if (lineCount >= linesPerChunk) {
                    boundaries.add(Pair(chunkStart, currentOffset + 1))
                    chunkStart = currentOffset + 1
                    lineCount = 0
                }
            }
            currentOffset++
        }
        // Add any remaining text as the last chunk.
        if (chunkStart < text.length) {
            boundaries.add(Pair(chunkStart, text.length))
        }
        return boundaries
    }

    fun parseTokens(tokens: List<Token>, textLength: Int): ASTNode {
        val rootChildren = mutableListOf<ASTNode>()
        val stack = ArrayDeque<MutableList<ASTNode>>() // Stack structure
        stack.addLast(rootChildren) // Equivalent to push()

        val bracketPairs = mapOf('(' to ')', '{' to '}', '[' to ']')
        val openingBrackets = bracketPairs.keys
        val closingBrackets = bracketPairs.values

        tokens.forEach { token ->
            when (token) {
                is Token.BracketToken -> {
                    when {
                        token.symbol in openingBrackets -> {
                            // Create a new block for an opening bracket.
                            val newBlock = BlockNode(token.start, token.end, emptyList())
                            stack.last().add(newBlock) // Add to the current scope
                            stack.addLast(mutableListOf()) // Start a new children list
                        }
                        token.symbol in closingBrackets -> {
                            // Ensure there is something to close
                            if (stack.size > 1) {
                                val children = stack.removeLast().toList() // Equivalent to pop()
                                val lastBlock = stack.last().lastOrNull() as? BlockNode

                                if (lastBlock != null) {
                                    val newBlock = lastBlock.copy(
                                        end = token.end,
                                        children = children
                                    )
                                    stack.last()[stack.last().lastIndex] = newBlock // Replace last block
                                } else {
                                    // Mismatched closing bracket
                                    stack.last().add(TokenNode(token, token.start, token.end))
                                }
                            } else {
                                // Unmatched closing bracket
                                stack.last().add(TokenNode(token, token.start, token.end))
                            }
                        }
                    }
                }
                else -> {
                    // For all other tokens, simply add a token node.
                    stack.last().add(TokenNode(token, token.start, token.end))
                }
            }
        }

        // Handle any remaining unmatched blocks.
        while (stack.size > 1) {
            val children = stack.removeLast().toList()
            val lastBlock = stack.last().lastOrNull() as? BlockNode
            if (lastBlock != null) {
                val newBlock = lastBlock.copy(
                    end = textLength,
                    children = children
                )
                stack.last()[stack.last().lastIndex] = newBlock
            }
        }

        return BlockNode(0, textLength, rootChildren.toList())
    }


    /**
     * Recursively merges two ASTs.
     * If an old node is structurally equal to the new node, returns the old one.
     * Otherwise, for BlockNodes, recursively merges children.
     */
    fun mergeAST(old: ASTNode?, new: ASTNode): ASTNode {
        if (old == new) return old ?: new

        return when (new) {
            is TokenNode -> {
                // Tokens have no children.
                if (old is TokenNode && old == new)
                    old
                else
                    new
            }
            is BlockNode -> {
                if (old is BlockNode &&
                    old.start == new.start && old.end == new.end &&
                    old.children.size == new.children.size
                ) {
                    // Recursively merge children.
                    val mergedChildren = new.children.indices.map { index ->
                        mergeAST(old.children[index], new.children[index])
                    }
                    val candidate = new.copy(children = mergedChildren)
                    if (old == candidate) old else candidate
                } else {
                    new
                }
            }
        }
    }


//    fun handleEdit(editRange: Interval, newText: String) {
//        // Build a new text string (or use a StringBuilder) from the edit.
//        val updatedText = buildString {
//            append(text.substring(0, editRange.start))
//            append(newText)
//            append(text.substring(editRange.end))
//        }
//
//        // Re‑tokenize/re‑parse only the affected region. For demonstration, we re‑parse the whole file.
//        val lexer = Lexer(updatedText)
//        val tokens = lexer.tokenize(0, updatedText.length)
//        val newAST = parseTokens(tokens, updatedText.length)
//
//        // Merge the old AST with the new AST so that unchanged nodes are re‑used.
//        val mergedAST = mergeAST(astRef.get(), newAST)
//
//        // Atomically swap in the new AST.
//        astRef.set(mergedAST)
//    }

    private fun queryClosestBefore(position: Int): ASTNode? {
        return intervalTree.queryClosestBefore(position)
    }

    private fun queryClosestAfter(position: Int): ASTNode? {
        return intervalTree.queryClosestAfter(position)
    }

    fun query(position: Int): ASTNode? {
        // Since the AST is immutable, no locking is required.
        return findNodeAt(astRef.get(), position)
    }

    fun findNodeAt(node: ASTNode, position: Int): ASTNode? {
        if (position < node.start || position > node.end)
            return null

        for (child in node.children) {
            val found = findNodeAt(child, position)
            if (found != null)
                return found
        }

        return node
    }

    private fun query(position: Int, p: Int): ASTNode? {
        val overlappingNodes = intervalTree.queryOverlapping(position, position)
        if (overlappingNodes.isNotEmpty()) {
            return overlappingNodes.minByOrNull { it.end - it.start } // Find the smallest node
        }

        // If no exact match, find the closest interval before and after
        val before = queryClosestBefore(position)
        val after = queryClosestAfter(position)

        return before ?: after
    }

//    private fun findReparseStart(editPos: Int): Int {
//        // Expand left until we find a non-trivial token change
//        var start = editPos
//        while (start > 0) {
//            val prevToken = query(start - 1)
//            if (prevToken == null || prevToken is TokenNode && prevToken.token.type in listOf(NEWLINE, BRACKET_OPEN))
//                break
//            start--
//        }
//        return start
//    }
//
//    private fun findReparseEnd(editPos: Int): Int {
//        // Expand right until a stable token (whitespace or punctuation)
//        var end = editPos
//        while (end < text.length) {
//            val nextToken = query(end)
//            if (nextToken == null || nextToken is TokenNode && nextToken.token.type in listOf(NEWLINE, BRACKET_CLOSE))
//                break
//            end++
//        }
//        return end
//    }
}