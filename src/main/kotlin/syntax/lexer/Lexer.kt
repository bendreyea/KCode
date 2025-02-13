package org.editor.syntax.lexer

class Lexer(
    val text: CharSequence,
    private val rules: List<TokenRule> = defaultRules(),
    var state: LexerState = LexerState.DEFAULT,
    val bracketStack: ArrayDeque<BracketInfo> = ArrayDeque()
) {
    var pos: Int = 0

    companion object {
        fun defaultRules(): List<TokenRule> = listOf(
            KeywordRule,
            BlockCommentRule,
            SingleLineCommentRule,
            StringLiteralRule,
            WhitespaceRule,
            NumberRule,
            IdentifierRule,
            BracketRule,
            SymbolRule,
        )
    }

    fun currentChar(): Char = if (pos < text.length) text[pos] else Char.MIN_VALUE

    fun lookAhead(offset: Int): Char =
        if (pos + offset < text.length) text[pos + offset] else Char.MIN_VALUE

    fun advance(n: Int = 1) {
        pos += n
    }

    /** Lexes the entire text of (for example) one source line. */
    fun tokenizeLine(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (pos < text.length) {
            var token: Token? = null
            for (rule in rules) {
                token = rule.match(this)
                if (token != null) break
            }
            if (token == null) {
                token = Token.ErrorToken(pos, pos + 1, "Unexpected character '${currentChar()}'")
                advance()
            }
            tokens.add(token)
        }

        return tokens
    }

    /** Standard multi‑line tokenize (if needed). */
    fun tokenize(start: Int, end: Int): List<Token> {
        pos = start
        val tokens = mutableListOf<Token>()
        while (pos < end) {
            var token: Token? = null
            for (rule in rules) {
                token = rule.match(this)
                if (token != null) break
            }
            if (token == null) {
                token = Token.ErrorToken(pos, pos + 1, "Unexpected character '${currentChar()}'")
                advance()
            }
            tokens.add(token)
        }
        return tokens
    }
}
