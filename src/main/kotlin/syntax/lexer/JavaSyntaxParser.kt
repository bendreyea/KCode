package org.editor.syntax.lexer

enum class TokenType {
    KEYWORD,     // e.g. "if", "else", "fun", "class"
    STRING,      // String literals
    COMMENT,     // Comments
    IDENTIFIER,  // Variable/function/class names
    NUMBER,      // Numbers
    SYMBOL,      // Punctuation, operators
    WHITESPACE,  // Spaces, tabs, line breaks
    UNKNOWN,
    ERROR,

    // -- Bracket tokens with 3 colors:
    BRACKET_LEVEL_0,
    BRACKET_LEVEL_1,
    BRACKET_LEVEL_2
}

enum class LexerState {
    DEFAULT,
    IN_BLOCK_COMMENT,
    IN_STRING
}

data class BracketInfo(
    val char: Char,  // '(' or '{' or '['
    val row: Int,
    val col: Int,
    val nestingColorIndex: Int  // 0..2
)
