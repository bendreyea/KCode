package syntax.tokenRules

import org.editor.syntax.StringLiteralRule
import org.editor.syntax.Token
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StringLiteralRuleTests {

    @Test
    fun `test string literal rule matches basic string`() {
        val input = "\"Hello, world!\""
        val lexer = createLexer(input)
        val token = StringLiteralRule.match(lexer)
        assertNotNull(token, "Expected StringLiteralRule to match")
        assertTrue(token is Token.StringToken)
        // In our implementation the token value includes the quotes.
        assertEquals("\"Hello, world!\"", token.value)
        assertEquals(input.length, token.end)
    }

    @Test
    fun `test string literal rule handles escape sequences`() {
        // For example, this string literal contains an escaped quote.
        val input = "\"Hello, \\\"world\\\"!\""
        val lexer = createLexer(input)
        val token = StringLiteralRule.match(lexer)
        assertNotNull(token, "Expected StringLiteralRule to match")
        assertTrue(token is Token.StringToken)
        assertEquals(input.length, token.end)
    }
}