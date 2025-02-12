//package org.editor.syntax
//
//import org.editor.syntax.intervalTree.IntervalTree
//import java.util.*
//import java.util.concurrent.ConcurrentHashMap
//import java.util.concurrent.atomic.AtomicInteger
//import java.util.concurrent.locks.ReentrantLock
//
///**
// * Represents the parser state at the start of a chunk:
// *   - The starting lexer state for this chunk.
// *   - The bracket stack as carried over from previous lines.
// */
//data class ParseCheckpoint(
//    val chunkStartLine: Int,            // e.g. 0, 100, 200, ...
//    var startLexerState: LexerState,    // how we begin parsing at this chunk
//    val startBracketStack: ArrayDeque<BracketInfo>
//)
//
///**
// * SyntaxHighlighter that highlights Java-like syntax
// */
//class SyntaxHighlighter {
//
//    companion object {
//        private const val DEFAULT_CHUNK_SIZE = 100
//    }
//
//    /** Number of lines per chunk. Adjusted dynamically. */
//    var chunkSize = DEFAULT_CHUNK_SIZE
//
//    /** Parser checkpoints, one per chunk. */
//    private val parseCheckpoints = mutableListOf<ParseCheckpoint>()
//
//    // Single data structure for all token highlights (including brackets).
//    private val lineIntervalTrees = ConcurrentHashMap<Int, IntervalTree<HighlightInterval>>()
//
//    // For skipping re-parse if line text + bracket/lexer state is unchanged.
//    private val lineHashes = ConcurrentHashMap<Int, AtomicInteger>()
//    private val lineEndStates = ConcurrentHashMap<Int, LexerState>()
//    private val carryStackPerLine = ConcurrentHashMap<Int, List<BracketInfo>>()
//    private val highlighterLock = ReentrantLock()
//
//    private val keywords = setOf(
//        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
//        "class", "const", "continue", "default", "do", "double", "else", "enum",
//        "extends", "final", "finally", "float", "for", "goto", "if", "implements",
//        "import", "instanceof", "int", "interface", "long", "native", "new",
//        "package", "private", "protected", "public", "record", "return",
//        "sealed", "short", "static", "strictfp", "super", "switch",
//        "synchronized", "this", "throw", "throws", "transient", "try",
//        "var", "volatile", "while", "yield"
//    )
//
//    /**
//     * Initialize parse checkpoints for a file of [totalRows] lines.
//     * If re-invoked, existing data structures are cleared.
//     */
//    fun initializeCheckpoints(totalRows: Int) {
//        highlighterLock.lock()
//        try {
//            parseCheckpoints.clear()
//            lineIntervalTrees.clear()
//            lineHashes.clear()
//            lineEndStates.clear()
//            carryStackPerLine.clear()
//
//            recalcChunkSize(totalRows)
//            val numChunks = (totalRows + chunkSize - 1) / chunkSize
//
//            for (i in 0 until numChunks) {
//                val startLine = i * chunkSize
//                parseCheckpoints.add(
//                    ParseCheckpoint(
//                        chunkStartLine = startLine,
//                        startLexerState = LexerState.DEFAULT,
//                        startBracketStack = ArrayDeque() // empty bracket stack
//                    )
//                )
//            }
//        }
//        finally {
//            highlighterLock.unlock()
//        }
//    }
//
//    /**
//     * Called when text changes at or near [changedLine].
//     * We compute which chunk is affected and re-parse if needed.
//     */
//    fun onLineChanged(
//        changedLine: Int,
//        getLineText: (Int) -> String,
//        getTotalRows: () -> Int
//    ) {
//        val totalRows = getTotalRows()
//        resizeCheckpoints(totalRows)
//
//        val chunkIndex = changedLine / chunkSize
//        parseIfNeeded(chunkIndex, getLineText, getTotalRows)
//    }
//
//    /**
//     * Dynamically resizes (and possibly re-initializes) parse checkpoints if the total line count
//     * has changed or if we want to recalc chunkSize for performance reasons.
//     */
//    fun resizeCheckpoints(newTotalRows: Int) {
//        // Possibly adjust chunkSize based on new total row count
//        val oldChunkSize = chunkSize
//        recalcChunkSize(newTotalRows)
//        val chunkSizeChanged = (chunkSize != oldChunkSize)
//
//        val oldNumChunks = parseCheckpoints.size
//        val newNumChunks = (newTotalRows + chunkSize - 1) / chunkSize
//
//        if (chunkSizeChanged) {
//            initializeCheckpoints(newTotalRows)
//            return
//        }
//
//        // If chunk size hasn't changed, we can partially grow or shrink parseCheckpoints.
//        if (newNumChunks == oldNumChunks) {
//            // No checkpoint changes needed
//            return
//        } else if (newNumChunks > oldNumChunks) {
//            // Need more parse checkpoints at the end
//            for (i in oldNumChunks until newNumChunks) {
//                val startLine = i * chunkSize
//                parseCheckpoints.add(
//                    ParseCheckpoint(
//                        chunkStartLine = startLine,
//                        startLexerState = LexerState.DEFAULT,
//                        startBracketStack = ArrayDeque()
//                    )
//                )
//            }
//        } else {
//            // We have too many parse checkpoints; remove the extras
//            parseCheckpoints.subList(newNumChunks, parseCheckpoints.size).clear()
//        }
//    }
//
//    /**
//     * Retrieve the intervals (syntax highlighting) for [row].
//     */
//    fun getAllIntervals(row: Int): List<HighlightInterval> {
//        return lineIntervalTrees[row]
//            ?.getAll()
//            ?.sortedBy { it.start }
//            ?: emptyList()
//    }
//
//    /**
//     * Re-parse chunk [chunkIndex], check if the final bracket/lexer state
//     * changed; if so, re-parse the next chunk, and so on.
//     */
//    private fun parseIfNeeded(
//        chunkIndex: Int,
//        getLineText: (Int) -> String,
//        getTotalRows: () -> Int
//    ) {
//        if (chunkIndex < 0 || chunkIndex >= parseCheckpoints.size) return
//
//        val checkpoint = parseCheckpoints[chunkIndex]
//        val (finalState, finalStack) = reparseChunk(
//            chunkStartLine = checkpoint.chunkStartLine,
//            startLexerState = checkpoint.startLexerState,
//            startBracketStack = ArrayDeque(checkpoint.startBracketStack),
//            getLineText = getLineText,
//            getTotalRows = getTotalRows
//        )
//
//        // Compare with next chunk’s known checkpoint
//        val nextIndex = chunkIndex + 1
//        if (nextIndex < parseCheckpoints.size) {
//            val nextCheckpoint = parseCheckpoints[nextIndex]
//            val changed = (
//                    nextCheckpoint.startLexerState != finalState ||
//                            !areBracketStacksEqual(nextCheckpoint.startBracketStack, finalStack)
//                    )
//            if (changed) {
//                // update next checkpoint
//                nextCheckpoint.startLexerState = finalState
//                nextCheckpoint.startBracketStack.clear()
//                nextCheckpoint.startBracketStack.addAll(finalStack)
//
//                // parse next chunk
//                parseIfNeeded(nextIndex, getLineText, getTotalRows)
//            }
//        }
//    }
//
//    /**
//     * Re-parse lines in [chunkStartLine .. chunkStartLine + chunkSize - 1]
//     * (or until end-of-document). Returns the final [LexerState] and bracket stack
//     * after parsing that chunk.
//     */
//    private fun reparseChunk(
//        chunkStartLine: Int,
//        startLexerState: LexerState,
//        startBracketStack: ArrayDeque<BracketInfo>,
//        getLineText: (Int) -> String,
//        getTotalRows: () -> Int
//    ): Pair<LexerState, ArrayDeque<BracketInfo>> {
//
//        var currentState = startLexerState
//        val bracketStack = ArrayDeque(startBracketStack)
//        val lastLineInChunk = chunkStartLine + chunkSize - 1
//        val totalRows = getTotalRows()
//
//        var row = chunkStartLine
//        while (row < totalRows && row <= lastLineInChunk) {
//            // parse line if needed (incremental + skip if unchanged)
//            val (endState, newStack) = parseLineIfNeeded(row, getLineText, currentState, bracketStack)
//
//            // Rebuild the IntervalTree for the row (parseLineIfNeeded does the logic for intervals).
//            currentState = endState
//
//            // we use the new snapshot:
//            bracketStack.clear()
//            bracketStack.addAll(newStack)
//
//            row++
//        }
//
//        return Pair(currentState, bracketStack)
//    }
//
//    /**
//     * Parse a single line if it has changed or if the bracket/lexer input state changed.
//     * If we skip, we just return the previously known end state.
//     */
//    fun parseLineIfNeeded(
//        row: Int,
//        getLineText: (Int) -> String,
//        incomingState: LexerState,
//        incomingBracketStack: ArrayDeque<BracketInfo>
//    ): Pair<LexerState, ArrayDeque<BracketInfo>> {
//
//        val text = getLineText(row)
//        val newHash = AtomicInteger(text.hashCode())
//        val oldHash = lineHashes[row]?.get()
//        val oldEndState = lineEndStates[row]
//        val oldCarryStack = carryStackPerLine[row]
//
//        // Check if we can skip
//        if (oldHash == newHash.get() &&
//            oldEndState != null &&
//            oldCarryStack != null &&
//            LexerStateCompatible(incomingState, oldEndState) &&
//            bracketStackCompatible(incomingBracketStack, oldCarryStack)
//        ) {
//
//            // => skip re-parse: the line text and incoming bracket state are the same
//            // Return old end state and bracket stack
//            return Pair(oldEndState, ArrayDeque(oldCarryStack))
//        }
//
//        // Otherwise, we must parse.
//        val parseResult = parseLine(
//            text,
//            row,
//            incomingState,
//            incomingBracketStack,
//            lineIntervalTrees
//        )
//
//        // Store new line hash and end-state so we can skip next time
//        lineHashes[row] = newHash
//        lineEndStates[row] = parseResult.endState
//        carryStackPerLine[row] = parseResult.newBracketStack.toList()
//
//        return Pair(parseResult.endState, ArrayDeque(parseResult.newBracketStack))
//    }
//
//    /**
//     * Actual parse of a single line with the given starting [startState] and [bracketStack].
//     * This function updates [lineIntervalTrees] with new intervals.
//     */
//    private fun parseLine(
//        line: String,
//        row: Int,
//        startState: LexerState,
//        bracketStack: ArrayDeque<BracketInfo>,
//        lineTreesMap: MutableMap<Int, IntervalTree<HighlightInterval>>
//    ): ParseLineResult {
//        val intervals = mutableListOf<HighlightInterval>()
//        var state = startState
//        var i = 0
//        val length = line.length
//
//        while (i < length) {
//            val c = line[i]
//
//            // 1) If inside a block comment, continue until "*/" or EOL
//            if (state == LexerState.IN_BLOCK_COMMENT) {
//                val commentStart = i
//                while (i < length) {
//                    if (i + 1 < length && line[i] == '*' && line[i + 1] == '/') {
//                        i += 2
//                        intervals.add(HighlightInterval(commentStart, i, TokenType.COMMENT))
//                        state = LexerState.DEFAULT
//                        break
//                    }
//                    i++
//                }
//                if (state == LexerState.IN_BLOCK_COMMENT) {
//                    // End of line but still in comment
//                    intervals.add(HighlightInterval(commentStart, length, TokenType.COMMENT))
//                    return storeIntervalsAndMakeResult(intervals, LexerState.IN_BLOCK_COMMENT, bracketStack, row)
//                }
//                continue
//            }
//
//            // 2) Single-line comment "//"
//            if (c == '/' && i + 1 < length && line[i + 1] == '/') {
//                intervals.add(HighlightInterval(i, length, TokenType.COMMENT))
//                break
//            }
//
//            // 3) Start of block comment "/*"
//            if (c == '/' && i + 1 < length && line[i + 1] == '*') {
//                val start = i
//                i += 2
//                state = LexerState.IN_BLOCK_COMMENT
//                continue
//            }
//
//            // 4) String literal
//            if (c == '"') {
//                val start = i
//                i++
//                while (i < length && line[i] != '"') {
//                    i++
//                }
//                if (i < length) {
//                    i++ // consume closing quote
//                }
//                intervals.add(HighlightInterval(start, i, TokenType.STRING))
//                continue
//            }
//
//            // 5) Whitespace
//            if (c.isWhitespace()) {
//                val start = i
//                while (i < length && line[i].isWhitespace()) i++
//                intervals.add(HighlightInterval(start, i, TokenType.WHITESPACE))
//                continue
//            }
//
//            // 6) Number
//            if (c.isDigit()) {
//                val start = i
//                while (i < length && line[i].isDigit()) i++
//                intervals.add(HighlightInterval(start, i, TokenType.NUMBER))
//                continue
//            }
//
//            // 7) Identifiers / Keywords
//            if (c.isLetter() || c == '_') {
//                val start = i
//                i++
//                while (i < length && (line[i].isLetterOrDigit() || line[i] == '_')) {
//                    i++
//                }
//                val lexeme = line.substring(start, i)
//                val type = if (keywords.contains(lexeme)) TokenType.KEYWORD else TokenType.IDENTIFIER
//                intervals.add(HighlightInterval(start, i, type))
//                continue
//            }
//
//            // 8) Brackets and other symbols
//            when (c) {
//                '(', '{', '[' -> {
//                    // Determine bracket color
//                    val colorIndex = bracketStack.size % 3
//                    val openBracketInfo = BracketInfo(c, row, i, colorIndex)
//                    bracketStack.addLast(openBracketInfo)
//
//                    // Immediately add a bracket highlight for the open bracket.
//                    val tokenType = when (colorIndex) {
//                        0 -> TokenType.BRACKET_LEVEL_0
//                        1 -> TokenType.BRACKET_LEVEL_1
//                        else -> TokenType.BRACKET_LEVEL_2
//                    }
//
//                    intervals.add(HighlightInterval(i, i + 1, tokenType))
//                }
//
//                ')', '}', ']' -> {
//                    // Check if top of bracket stack matches
//                    if (bracketStack.isNotEmpty()) {
//                        val top = bracketStack.last()
//                        if (isMatchingPair(top.char, c)) {
//                            // It's a match => pop
//                            bracketStack.removeLast()
//
//                            val colorIndex = top.nestingColorIndex
//                            val tokenType = when (colorIndex) {
//                                0 -> TokenType.BRACKET_LEVEL_0
//                                1 -> TokenType.BRACKET_LEVEL_1
//                                else -> TokenType.BRACKET_LEVEL_2
//                            }
//
//                            if (top.row == row) {
//                                // Same line => insert intervals for open and close bracket
//                                intervals.add(HighlightInterval(top.col, top.col + 1, tokenType))
//                                intervals.add(HighlightInterval(i, i + 1, tokenType))
//                            } else {
//                                // Different line => we must insert the open bracket highlight
//                                // into the previous line’s IntervalTree
//                                lineTreesMap[top.row]?.insert(HighlightInterval(top.col, top.col + 1, tokenType))
//                                // Insert the close bracket highlight in the current line
//                                intervals.add(HighlightInterval(i, i + 1, tokenType))
//                            }
//                        } else {
//                            // Not matching => highlight as an error ?
//                        }
//                    } else {
//                        // No bracket to match => unmatched close bracket ?
//                    }
//                }
//
//                else -> {
//                    // Just a symbol (operator, punctuation, etc.)
//                    intervals.add(HighlightInterval(i, i + 1, TokenType.SYMBOL))
//                }
//            }
//            i++
//        }
//
//        return storeIntervalsAndMakeResult(intervals, state, bracketStack, row)
//    }
//
//    /** Decide a new chunk size based on total lines. */
//    private fun recalcChunkSize(totalRows: Int) {
//        chunkSize = when {
//            totalRows < 2000 -> 15
//            totalRows < 5000 -> 30
//            totalRows < 10000 -> 60
//            totalRows < 50000 -> 90
//            else -> 120
//        }
//    }
//
//    /** Save intervals in the lineIntervalTrees, return the parse result. */
//    private fun storeIntervalsAndMakeResult(
//        intervals: List<HighlightInterval>,
//        endState: LexerState,
//        bracketStack: ArrayDeque<BracketInfo>,
//        row: Int
//    ): ParseLineResult {
//        val tree = IntervalTree<HighlightInterval>()
//        intervals.forEach { tree.insert(it) }
//        lineIntervalTrees[row] = tree
//        return ParseLineResult(intervals, endState, bracketStack)
//    }
//
//    /** Compare bracket stacks by content. */
//    private fun areBracketStacksEqual(
//        stackA: ArrayDeque<BracketInfo>,
//        stackB: ArrayDeque<BracketInfo>
//    ): Boolean {
//        if (stackA.size != stackB.size) return false
//        val iterA = stackA.iterator()
//        val iterB = stackB.iterator()
//        while (iterA.hasNext() && iterB.hasNext()) {
//            if (iterA.next() != iterB.next()) return false
//        }
//        return true
//    }
//
//    private fun isMatchingPair(open: Char, close: Char): Boolean {
//        return (open == '(' && close == ')')
//                || (open == '{' && close == '}')
//                || (open == '[' && close == ']')
//    }
//
//    /** Decide if the old end-state is "compatible" with the new incoming state. */
//    private fun LexerStateCompatible(incoming: LexerState, oldEndState: LexerState): Boolean {
//        return (incoming == oldEndState)
//    }
//
//    /** Compare bracket stacks from carryStack with the incoming bracket stack. */
//    private fun bracketStackCompatible(
//        incomingStack: ArrayDeque<BracketInfo>,
//        oldStackList: List<BracketInfo>
//    ): Boolean {
//        if (incomingStack.size != oldStackList.size) return false
//        // Compare elements
//        for (i in incomingStack.indices) {
//            if (incomingStack.elementAt(i) != oldStackList[i]) {
//                return false
//            }
//        }
//        return true
//    }
//
//    private data class ParseLineResult(
//        val intervals: List<HighlightInterval>,
//        val endState: LexerState,
//        val newBracketStack: ArrayDeque<BracketInfo>
//    )
//}