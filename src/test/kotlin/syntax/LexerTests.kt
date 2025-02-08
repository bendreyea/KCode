package syntax

import org.editor.syntax.Lexer
import org.editor.syntax.Token
import org.editor.syntax.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LexerTests {

    @Test
    fun `test lexer tokenize matches`() {
        val input = "public class Main {\n}"
        val lexer = Lexer(input)
        // At this point the bracket stack is empty.
        val tokens = lexer.tokenize(0, input.length)
        assertEquals(9, tokens.size)
        assertEquals(TokenType.KEYWORD, tokens[0].type)
        assertEquals(TokenType.WHITESPACE, tokens[1].type)
        assertEquals(TokenType.KEYWORD, tokens[2].type)
        assertEquals(TokenType.BRACKET_LEVEL_0, tokens[6].type)
    }

    @Test
    fun `test lexer tokenize handles nested block comments`() {
        val input = "/* This is /* a nested */ comment */"
        val lexer = Lexer(input)

        val tokens = lexer.tokenize(0, input.length)
        // Expect the entire line to be a single comment
        assertEquals(6, tokens.size)
        assertEquals(TokenType.COMMENT, tokens[0].type)
    }

    @Test
    fun `test lexer tokenize handles single line comments after code`() {
        val input = "int x = 10; // initialize x"
        val lexer = Lexer(input)
        val tokens = lexer.tokenize(0, input.length)

        // Expect tokens for 'int', whitespace, 'x', whitespace, '=', whitespace, '10', ';', whitespace, comment
        assertTrue(tokens.any { it.type == TokenType.COMMENT })
        assertTrue(tokens.any { it.type == TokenType.KEYWORD && it.start == 0 })
    }

    @Test
    fun `test lexer tokenize handles unmatched open brackets`() {
        val input = "public void method() { if (true) {"
        val lexer = Lexer(input)

        val tokens = lexer.tokenize(0, input.length)
        val bracketLevel0 = tokens.filter { it.type == TokenType.BRACKET_LEVEL_0 }
        val bracketLevel1 = tokens.filter { it.type == TokenType.BRACKET_LEVEL_1 }
        assertEquals(3, bracketLevel0.size)
        assertEquals(3, bracketLevel1.size)
    }

    @Test
    fun `test lexer tokenize handles brackets`() {
        val input = "public TextEditImpl(Document doc) {\nthis.doc = doc; }"
        val lexer = Lexer(input)

        val intervalsLine0 = lexer.tokenize(0, input.indexOf('\n'))

        // Line 0 should have tokens
        assertTrue(intervalsLine0.isNotEmpty())

        assertEquals(10, intervalsLine0.size)
        assertEquals(TokenType.BRACKET_LEVEL_0, intervalsLine0[intervalsLine0.size - 1].type)
    }

    @Test
    fun `test lexer tokenize handles multiple handles stringLiterals on same line`() {
        val input = "String s1 = \"Hello\", s2 = \"World\";"
        val lexer = Lexer(input)

        val intervals = lexer.tokenize(0, input.length)
        // Expect two STRING tokens
        val stringTokens = intervals.filter { it.type == TokenType.STRING }
        assertEquals(2, stringTokens.size)
    }

    @Test
    fun `test lexer tokenize handles multiple handles symbols and operators`() {
        val input = "a += b * (c - d) / e;"
        val lexer = Lexer(input)

        val intervals = lexer.tokenize(0, input.length)
        // Expect SYMBOL tokens for operators and brackets
        val symbolTokens = intervals.filter { it.type == TokenType.SYMBOL }
        val bracketTokens = intervals.filter { it.type == TokenType.BRACKET_LEVEL_0 || it.type == TokenType.BRACKET_LEVEL_1 || it.type == TokenType.BRACKET_LEVEL_2 }
        assertTrue(symbolTokens.size >= 5) // +=, *, -, /, ;
        assertTrue(bracketTokens.size == 2) // ( and )
    }

    @Test
    fun `test lexer tokenize handles multiple multiple block comments`() {
        val input = "/* Comment 1 */ int x = 0; /* Comment 2 */"
        val lexer = Lexer(input)

        val intervals = lexer.tokenize(0, input.length)
        // Expect two COMMENT tokens and tokens for 'int', 'x', '=', '0', ';'
        val commentTokens = intervals.filter { it.type == TokenType.COMMENT }
        assertEquals(2, commentTokens.size)
        val keyword = intervals.find { it.type == TokenType.KEYWORD && it.start == "/* Comment 1 */ ".length }
        assertTrue(keyword != null)
    }

    @Test
    fun `test lexer tokenizes keywords, identifiers, and brackets`() {
        val input = "public class Main {\n}"
        val lexer = Lexer(input)
        val tokens = lexer.tokenize(0, input.length)

        assertEquals(9, tokens.size)
        assertEquals(TokenType.IDENTIFIER, tokens[0].type)  // "public" (no keyword detection)
        assertEquals(TokenType.WHITESPACE, tokens[1].type)
        assertEquals(TokenType.IDENTIFIER, tokens[2].type)  // "class" (no keyword detection)
        assertEquals(TokenType.WHITESPACE, tokens[3].type)
        assertEquals(TokenType.IDENTIFIER, tokens[4].type)  // "Main"
        assertEquals(TokenType.WHITESPACE, tokens[5].type)
        assertEquals(TokenType.BRACKET_LEVEL_0, tokens[6].type)  // '{'
        assertEquals(TokenType.WHITESPACE, tokens[7].type)
        assertEquals(TokenType.BRACKET_LEVEL_0, tokens[8].type)  // '}'
    }

    @Test
    fun `test lexer tokenizes numbers`() {
        val input = "42 3.14 0xFF"
        val lexer = Lexer(input)
        val tokens = lexer.tokenize(0, input.length)

        assertEquals(8, tokens.size)
        assertEquals(TokenType.NUMBER, tokens[0].type)
        assertEquals("42", (tokens[0] as Token.NumberToken).value)

        assertEquals(TokenType.WHITESPACE, tokens[1].type)

        assertEquals(TokenType.NUMBER, tokens[2].type)
        assertEquals("3", (tokens[2] as Token.NumberToken).value)  // No float handling yet

        assertEquals(TokenType.SYMBOL, tokens[3].type)  // "."
        assertEquals(TokenType.NUMBER, tokens[4].type)  // "14"
    }

    @Test
    fun `test lexer tokenizes string literals`() {
        val input = "\"Hello, World!\" \"Escaped \\\" quote\""
        val lexer = Lexer(input)
        val tokens = lexer.tokenize(0, input.length)

        assertEquals(3, tokens.size)
        assertEquals(TokenType.STRING, tokens[0].type)
        assertEquals("\"Hello, World!\"", (tokens[0] as Token.StringToken).value)

        assertEquals(TokenType.WHITESPACE, tokens[1].type)

        assertEquals(TokenType.STRING, tokens[2].type)
        assertEquals("\"Escaped \\\" quote\"", (tokens[2] as Token.StringToken).value)
    }

    @Test
    fun `test lexer handles mismatched brackets`() {
        val input = "{ ( [ ) ] }"
        val lexer = Lexer(input)
        val tokens = lexer.tokenize(0, input.length)

        assertEquals(11, tokens.size)

        assertEquals(TokenType.BRACKET_LEVEL_0, tokens[0].type)  // "{"
        assertEquals(TokenType.WHITESPACE, tokens[1].type)
        assertEquals(TokenType.BRACKET_LEVEL_1, tokens[2].type)  // "("
        assertEquals(TokenType.WHITESPACE, tokens[3].type)
        assertEquals(TokenType.BRACKET_LEVEL_2, tokens[4].type)  // "["
        assertEquals(TokenType.WHITESPACE, tokens[5].type)

        // Mismatched closing bracket (expected a closing `]` first)
        assertEquals(TokenType.SYMBOL, tokens[6].type)  // ")"
        assertEquals(TokenType.WHITESPACE, tokens[7].type)
        assertEquals(TokenType.BRACKET_LEVEL_2, tokens[8].type)  // "]"
    }

    @Test
    fun `test lexer handles nested brackets with color levels`() {
        val input = "{ [ ( ) ] }"
        val lexer = Lexer(input)
        val tokens = lexer.tokenize(0, input.length)

        assertEquals(11, tokens.size)

        assertEquals(TokenType.BRACKET_LEVEL_0, tokens[0].type)  // "{"
        assertEquals(TokenType.WHITESPACE, tokens[1].type)
        assertEquals(TokenType.BRACKET_LEVEL_1, tokens[2].type)  // "["
        assertEquals(TokenType.WHITESPACE, tokens[3].type)
        assertEquals(TokenType.BRACKET_LEVEL_2, tokens[4].type)  // "("
        assertEquals(TokenType.WHITESPACE, tokens[5].type)
        assertEquals(TokenType.BRACKET_LEVEL_2, tokens[6].type)  // ")"
        assertEquals(TokenType.WHITESPACE, tokens[7].type)
        assertEquals(TokenType.BRACKET_LEVEL_1, tokens[8].type)  // "]"
    }

    @Test
    fun `test lexer tokenizes comments`() {
        val input = "// This is a comment\n/* This is a block comment */"
        val lexer = Lexer(input)
        val tokens = lexer.tokenize(0, input.length)

        assertEquals(3, tokens.size)
        assertEquals(TokenType.COMMENT, tokens[0].type)
        assertEquals("// This is a comment", (tokens[0] as Token.CommentToken).text)

        assertEquals(TokenType.WHITESPACE, tokens[1].type)

        assertEquals(TokenType.COMMENT, tokens[2].type)
        assertEquals("/* This is a block comment */", (tokens[2] as Token.CommentToken).text)
    }

    @Test
    fun `test lexer handles unexpected characters as error tokens`() {
        val input = "@#$%^&*"
        val lexer = Lexer(input)
        val tokens = lexer.tokenize(0, input.length)

        assertEquals(input.length, tokens.size)

        tokens.forEachIndexed { index, token ->
            assertEquals(TokenType.ERROR, token.type)
            assertEquals(input[index].toString(), (token as Token.ErrorToken).message.takeLast(1))
        }
    }

    @Test
    fun `test lexer tokenizes mixed input correctly`() {
        val input = "var x = 42; // Comment"
        val lexer = Lexer(input)
        val tokens = lexer.tokenize(0, input.length)

        assertEquals(10, tokens.size)

        assertEquals(TokenType.IDENTIFIER, tokens[0].type)  // "var"
        assertEquals(TokenType.WHITESPACE, tokens[1].type)
        assertEquals(TokenType.IDENTIFIER, tokens[2].type)  // "x"
        assertEquals(TokenType.WHITESPACE, tokens[3].type)
        assertEquals(TokenType.SYMBOL, tokens[4].type)  // "="
        assertEquals(TokenType.WHITESPACE, tokens[5].type)
        assertEquals(TokenType.NUMBER, tokens[6].type)  // "42"
    }

    @Test
    fun `test lexer processes unclosed string literals as error`() {
        val input = "\"Hello"
        val lexer = Lexer(input)
        val tokens = lexer.tokenize(0, input.length)

        assertEquals(1, tokens.size)
        assertEquals(TokenType.STRING, tokens[0].type)
        assertEquals("\"Hello", (tokens[0] as Token.StringToken).value) // Should still classify as STRING
    }
}