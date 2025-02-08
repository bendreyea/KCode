package org.editor.syntax

class Lexer(val text: CharSequence, private val rules: List<TokenRule> = defaultRules()) {
    var pos: Int = 0
        private set

    val bracketStack: ArrayDeque<BracketInfo> = ArrayDeque()

    companion object {
        fun defaultRules(): List<TokenRule> = listOf(
            BlockCommentRule,
            SingleLineCommentRule,
            StringLiteralRule,
            WhitespaceRule,
            NumberRule,
            IdentifierRule,
            BracketRule,
            SymbolRule
        )
    }

    fun currentChar(): Char = if (pos < text.length) text[pos] else Char.MIN_VALUE

    fun lookAhead(offset: Int): Char =
        if (pos + offset < text.length) text[pos + offset] else Char.MIN_VALUE

    fun advance(n: Int = 1) {
        pos += n
    }

    fun tokenize(start: Int, end: Int): List<Token> {
        val tokens = mutableListOf<Token>()
        pos = start
        while (pos < end) {
            var token: Token? = null

            // Try each rule in order.
            for (rule in rules) {
                token = rule.match(this)
                if (token != null)
                    break
            }

            if (token == null) {
                // If no rule applies, produce an error token and consume one character.
                token = Token.ErrorToken(pos, pos + 1, "Unexpected character '${currentChar()}'")
                advance()
            }

            tokens.add(token)
        }

        return tokens
    }
}