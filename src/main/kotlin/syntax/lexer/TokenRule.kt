package org.editor.syntax.lexer


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
                    // We’re now outside the block comment.
                    lexer.state = LexerState.DEFAULT
                    return Token.CommentToken(start, lexer.pos, lexer.text.substring(start, lexer.pos))
                } else {
                    lexer.advance()
                }
            }
            // Reaching here means we hit end-of-line/text without closing.
            lexer.state = LexerState.IN_BLOCK_COMMENT
            return Token.CommentToken(start, lexer.pos, lexer.text.substring(start, lexer.pos))
        }
        return null
    }
}

object StringLiteralRule : TokenRule {
    override fun match(lexer: Lexer): Token? {
        if (lexer.currentChar() != '"') return null
        val start = lexer.pos
        lexer.advance() // skip opening quote
        while (lexer.pos < lexer.text.length) {
            when (lexer.currentChar()) {
                '\\' -> lexer.advance(2)
                '"' -> {
                    lexer.advance() // closing quote found
                    lexer.state = LexerState.DEFAULT
                    return Token.StringToken(start, lexer.pos, lexer.text.substring(start, lexer.pos))
                }
                else -> lexer.advance()
            }
        }
        // If we get here, no closing quote was found.
        lexer.state = LexerState.IN_STRING
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
        while (lexer.pos < lexer.text.length &&
            (lexer.currentChar().isLetterOrDigit() || lexer.currentChar() == '_')) {
            lexer.advance()
        }
        return Token.IdentifierToken(start, lexer.pos, lexer.text.substring(start, lexer.pos))
    }
}

object BracketRule : TokenRule {
    private val openBrackets = setOf('(', '{', '[')
    private val closeBrackets = setOf(')', '}', ']')
    override fun match(lexer: Lexer): Token? {
        val ch = lexer.currentChar()
        if (ch in openBrackets) {
            val start = lexer.pos
            val colorIndex = lexer.bracketStack.size % 3
            lexer.bracketStack.addLast(BracketInfo(ch, start, lexer.bracketStack.size + 1, colorIndex))
            lexer.advance()
            val tokenType = when (colorIndex) {
                0 -> TokenType.BRACKET_LEVEL_0
                1 -> TokenType.BRACKET_LEVEL_1
                else -> TokenType.BRACKET_LEVEL_2
            }
            return Token.BracketToken(start, lexer.pos, ch, colorIndex, tokenType)
        }
        if (ch in closeBrackets) {
            val start = lexer.pos
            if (lexer.bracketStack.isNotEmpty()) {
                val last = lexer.bracketStack.last()
                if (isMatchingPair(last.char, ch)) {
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
            // No matching open bracket: treat as a symbol.
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
        val ch = lexer.currentChar()
        if (!ch.isLetterOrDigit() && !ch.isWhitespace()) {
            val start = lexer.pos
            lexer.advance()
            return Token.SymbolToken(start, lexer.pos, ch)
        }
        return null
    }
}

object KeywordRule : TokenRule {
    private val keywords = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
        "class", "const", "continue", "default", "do", "double", "else", "enum",
        "extends", "final", "finally", "float", "for", "goto", "if", "implements",
        "import", "instanceof", "int", "interface", "long", "native", "new",
        "package", "private", "protected", "public", "record", "return",
        "sealed", "short", "static", "strictfp", "super", "switch",
        "synchronized", "this", "throw", "throws", "transient", "try",
        "var", "volatile", "while", "yield"
    )
    override fun match(lexer: Lexer): Token? {
        if (!lexer.currentChar().isLetter() && lexer.currentChar() != '_')
            return null
        val start = lexer.pos
        var tempPos = lexer.pos
        while (tempPos < lexer.text.length &&
            (lexer.text[tempPos].isLetterOrDigit() || lexer.text[tempPos] == '_')) {
            tempPos++
        }
        val lexeme = lexer.text.substring(start, tempPos)
        if (lexeme in keywords) {
            lexer.pos = tempPos
            return Token.KeywordToken(start, tempPos, lexeme)
        }
        return null
    }
}

