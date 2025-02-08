package org.editor.syntax


interface TokenRule {
    /**
     * Tries to match a token at the lexer's current position.
     * If successful, the lexer’s position is advanced and the token is returned.
     * Otherwise, returns null.
     */
    fun match(lexer: Lexer): Token?
}

object SingleLineCommentRule : TokenRule {
    override fun match(lexer: Lexer): Token? {
        if (lexer.currentChar() == '/' && lexer.lookAhead(1) == '/') {
            val start = lexer.pos
            while (lexer.pos < lexer.text.length && lexer.currentChar() != '\n') {
                lexer.advance()
            }
            return Token.CommentToken(start, lexer.pos, lexer.text.substring(start, lexer.pos))
        }
        return null
    }
}

object BlockCommentRule : TokenRule {
    override fun match(lexer: Lexer): Token? {
        if (lexer.currentChar() == '/' && lexer.lookAhead(1) == '*') {
            val start = lexer.pos
            lexer.advance(2)
            while (lexer.pos < lexer.text.length) {
                if (lexer.currentChar() == '*' && lexer.lookAhead(1) == '/') {
                    lexer.advance(2)
                    break
                } else {
                    lexer.advance()
                }
            }
            return Token.CommentToken(start, lexer.pos, lexer.text.substring(start, lexer.pos))
        }
        return null
    }
}

object StringLiteralRule : TokenRule {
    override fun match(lexer: Lexer): Token? {
        if (lexer.currentChar() != '"') return null
        val start = lexer.pos
        lexer.advance() // skip the opening quote
        while (lexer.pos < lexer.text.length) {
            when (lexer.currentChar()) {
                '\\' -> lexer.advance(2) // handle escape sequences by skipping both characters
                '"' -> {
                    lexer.advance() // consume the closing quote
                    break
                }
                else -> lexer.advance()
            }
        }
        return Token.StringToken(start, lexer.pos, lexer.text.substring(start, lexer.pos))
    }
}

object WhitespaceRule : TokenRule {
    override fun match(lexer: Lexer): Token? {
        if (!lexer.currentChar().isWhitespace()) return null
        val start = lexer.pos
        while (lexer.pos < lexer.text.length && lexer.currentChar().isWhitespace()) {
            lexer.advance()
        }
        return Token.WhiteSpaceToken(start, lexer.pos, lexer.text.substring(start, lexer.pos))
    }
}

object NumberRule : TokenRule {
    override fun match(lexer: Lexer): Token? {
        if (!lexer.currentChar().isDigit()) return null
        val start = lexer.pos
        while (lexer.pos < lexer.text.length && lexer.currentChar().isDigit()) {
            lexer.advance()
        }
        return Token.NumberToken(start, lexer.pos, lexer.text.substring(start, lexer.pos))
    }
}

object IdentifierRule : TokenRule {
    override fun match(lexer: Lexer): Token? {
        if (!lexer.currentChar().isLetter() && lexer.currentChar() != '_') return null
        val start = lexer.pos
        while (lexer.pos < lexer.text.length && (lexer.currentChar().isLetterOrDigit() || lexer.currentChar() == '_')) {
            lexer.advance()
        }
        val lexeme = lexer.text.substring(start, lexer.pos)
        // Here you can decide whether this is a keyword (by checking against a keyword set)
        return Token.IdentifierToken(start, lexer.pos, lexeme)
    }
}

object BracketRule : TokenRule {
    // These are the open and closing bracket characters.
    private val openBrackets = setOf('(', '{', '[')
    private val closeBrackets = setOf(')', '}', ']')

    override fun match(lexer: Lexer): Token? {
        val ch = lexer.currentChar()
        if (ch in openBrackets) {
            val start = lexer.pos
            // Calculate color index based on current nesting.
            val colorIndex = lexer.bracketStack.size % 3
            // Push an entry onto the bracket stack.
            lexer.bracketStack.addLast(BracketInfo(ch, start, 1, colorIndex))
            lexer.advance() // Consume the bracket.
            val tokenType = when (colorIndex) {
                0 -> TokenType.BRACKET_LEVEL_0
                1 -> TokenType.BRACKET_LEVEL_1
                else -> TokenType.BRACKET_LEVEL_2
            }
            return Token.BracketToken(start, lexer.pos, ch, colorIndex, tokenType)
        }
        if (ch in closeBrackets) {
            val start = lexer.pos
            // Check if we have a matching open bracket.
            if (lexer.bracketStack.isNotEmpty()) {
                val last = lexer.bracketStack.last()
                if (isMatchingPair(last.char, ch)) {
                    // Matching bracket found: pop the open bracket.
                    lexer.bracketStack.removeLast()
                    val colorIndex = last.nestingColorIndex
                    val tokenType = when (colorIndex) {
                        0 -> TokenType.BRACKET_LEVEL_0
                        1 -> TokenType.BRACKET_LEVEL_1
                        else -> TokenType.BRACKET_LEVEL_2
                    }
                    lexer.advance()
                    return Token.BracketToken(start, lexer.pos, ch, colorIndex, tokenType)
                }
            }
            // If no matching open bracket, treat it as a generic symbol (or you might want to return an error).
            lexer.advance()
            return Token.SymbolToken(start, lexer.pos, ch)
        }

        return null
    }

    private fun isMatchingPair(open: Char, close: Char): Boolean =
        (open == '(' && close == ')') ||
                (open == '{' && close == '}') ||
                (open == '[' && close == ']')
}


object SymbolRule : TokenRule {
    override fun match(lexer: Lexer): Token? {
        // This rule is a fallback for symbols like operators or punctuation.
        val ch = lexer.currentChar()
        if (!ch.isLetterOrDigit() && !ch.isWhitespace()) {
            val start = lexer.pos
            lexer.advance()
            return Token.SymbolToken(start, lexer.pos, ch)
        }

        return null
    }
}

