package org.editor.syntax

import org.editor.syntax.intervalTree.Interval

enum class TokenType {
    KEYWORD,     // e.g. "if", "else", "fun", "class"
    STRING,      // String literals
    COMMENT,     // Comments
    IDENTIFIER,  // Variable/function/class names
    NUMBER,      // Numbers
    SYMBOL,      // Punctuation, operators
    WHITESPACE,  // Spaces, tabs, line breaks
    UNKNOWN,

    // -- Bracket tokens with 3 colors:
    BRACKET_LEVEL_0,
    BRACKET_LEVEL_1,
    BRACKET_LEVEL_2
}

data class HighlightInterval(
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

enum class LexerState {
    DEFAULT,
    IN_BLOCK_COMMENT
}

data class BracketInfo(
    val char: Char,  // '(' or '{' or '['
    val row: Int,
    val col: Int,
    val nestingColorIndex: Int  // 0..2
)

fun isMatchingPair(open: Char, close: Char): Boolean {
    return (open == '(' && close == ')')
            || (open == '{' && close == '}')
            || (open == '[' && close == ']')
}
