package org.editor.syntax.highlighter

import org.editor.syntax.lexer.BracketInfo
import org.editor.syntax.lexer.LexerState
import org.editor.syntax.lexer.Token
import org.editor.syntax.lexer.TokenType
import org.editor.syntax.lexer.Lexer
import org.editor.syntax.intervalTree.Interval

data class HighlightInterval(
    val offset: Int,
    override val start: Int, // inclusive
    override val end: Int,   // exclusive
    val type: TokenType
) : Interval {

    override fun compareTo(other: Interval): Int {
        return when {
            start > other.start -> 1
            start < other.start -> -1
            end > other.end -> 1
            end < other.end -> -1
            else -> 0
        }
    }
}

data class ParseLineResult(
    val ast: ASTNode,
    val highlightInterval: List<HighlightInterval>,
    val endState: LexerState,
    val newBracketStack: ArrayDeque<BracketInfo>
)

object BracketConfig {
    val pairs: Map<Char, Char> = mapOf('(' to ')', '{' to '}', '[' to ']')
    val opening: Set<Char> = pairs.keys
    val closing: Set<Char> = pairs.values.toSet()
}

class Parser {

    fun parseLine(
        lineText: String,
        start: Long,
        end: Long,
        startState: LexerState,
        startBracketStack: ArrayDeque<BracketInfo>
    ): ParseLineResult {
        // Initialize a new lexer with the provided state and bracket stack.
        val lexer = Lexer(lineText, state = startState, bracketStack = ArrayDeque(startBracketStack))
        val tokens = lexer.tokenizeLine()
        val intervals =
            tokens.map { HighlightInterval(start.toInt(), start.toInt() + it.start, start.toInt() + it.end, it.type) }
        val ast = parseTokens(tokens, start, end)
        return ParseLineResult(ast, intervals, lexer.state, ArrayDeque(lexer.bracketStack))
    }

    fun parseTokens(tokens: List<Token>, start: Long, end: Long): ASTNode {
        // Your existing logic to build the AST from tokens.
        val rootChildren = mutableListOf<ASTNode>()
        val stack = ArrayDeque<Pair<MutableList<ASTNode>, Int>>()
        stack.addLast(rootChildren to 0)

        tokens.forEach { token ->
            if (token is Token.BracketToken) {
                when (token.symbol) {
                    in BracketConfig.opening -> {
                        val newLevel = stack.last().second + 1
                        val newBlock = BlockNode(
                            start = start.toInt() + token.start,
                            end = start.toInt() + token.end, // will update on closing
                            children = emptyList(),
                            bracketType = token.symbol,
                            nestingLevel = newLevel
                        )
                        stack.last().first.add(newBlock)
                        stack.addLast(mutableListOf<ASTNode>() to newLevel)
                    }

                    in BracketConfig.closing -> {
                        if (stack.size > 1) {
                            val (children, _) = stack.removeLast()
                            val currentList = stack.last().first
                            val lastNode = currentList.lastOrNull()
                            if (lastNode is BlockNode && lastNode.bracketType != null) {
                                val updatedBlock = lastNode.copy(
                                    end = start.toInt() + token.end,
                                    children = children
                                )
                                currentList[currentList.lastIndex] = updatedBlock
                            } else {
                                currentList.add(
                                    TokenNode(
                                        token,
                                        start.toInt() + token.start,
                                        start.toInt() + token.end
                                    )
                                )
                            }
                        } else {
                            stack.last().first.add(
                                TokenNode(
                                    token,
                                    start.toInt() + token.start,
                                    start.toInt() + token.end
                                )
                            )
                        }
                    }

                    else -> stack.last().first.add(
                        TokenNode(
                            token,
                            start.toInt() + token.start,
                            start.toInt() + token.end
                        )
                    )
                }
            } else {
                stack.last().first.add(TokenNode(token, start.toInt() + token.start, start.toInt() + token.end))
            }
        }
        while (stack.size > 1) {
            val (children, _) = stack.removeLast()
            val parentList = stack.last().first
            val lastNode = parentList.lastOrNull()
            if (lastNode is BlockNode && lastNode.bracketType != null) {
                val updatedBlock = lastNode.copy(end = end.toInt(), children = children)
                parentList[parentList.lastIndex] = updatedBlock
            }
        }

        return BlockNode(start.toInt(), end.toInt(), rootChildren, bracketType = null, nestingLevel = 0)
    }
//
//    fun astToHighlightIntervals(ast: ASTNode): List<HighlightInterval> {
//        val highlights = mutableListOf<HighlightInterval>()
//        fun traverse(node: ASTNode) {
//            when (node) {
//                is TokenNode -> highlights.add(HighlightInterval(node.start, node.end, node.token.type))
//                is BlockNode -> node.children.forEach(::traverse)
//            }
//        }
//        traverse(ast)
//        return highlights
//    }
}