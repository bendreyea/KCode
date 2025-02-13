package org.editor.syntax.lexer

sealed class Token(open val start: Int, open val end: Int, open val type: TokenType) {
    data class CommentToken(override val start: Int, override val end: Int, val text: String) :
        Token(start, end, TokenType.COMMENT)

    data class StringToken(override val start: Int, override val end: Int, val value: String) :
        Token(start, end, TokenType.STRING)

    data class KeywordToken(override val start: Int, override val end: Int, val value: String) :
        Token(start, end, TokenType.KEYWORD)

    data class WhiteSpaceToken(override val start: Int, override val end: Int, val text: String) :
        Token(start, end, TokenType.WHITESPACE)

    data class NumberToken(override val start: Int, override val end: Int, val value: String) :
        Token(start, end, TokenType.NUMBER)

    data class IdentifierToken(override val start: Int, override val end: Int, val identifier: String) :
        Token(start, end, TokenType.IDENTIFIER)

    data class SymbolToken(override val start: Int, override val end: Int, val symbol: Char) :
        Token(start, end, TokenType.SYMBOL)

    data class ErrorToken(override val start: Int, override val end: Int, val message: String) :
        Token(start, end, TokenType.ERROR)

    data class BracketToken(
        override val start: Int,
        override val end: Int,
        val symbol: Char,
        val level: Int,
        override val type: TokenType
    ) : Token(start, end, type)
}
