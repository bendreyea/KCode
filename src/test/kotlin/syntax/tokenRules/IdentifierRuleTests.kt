package syntax.tokenRules

import org.editor.syntax.lexer.IdentifierRule
import org.editor.syntax.lexer.Token
import kotlin.test.*

class IdentifierRuleTests {
    @Test
    fun `test identifier rule matches simple identifier`() {
        val input = "identifier"
        val lexer = createLexer(input)
        val token = IdentifierRule.match(lexer)
        assertNotNull(token, "Expected IdentifierRule to match")
        assertTrue(token is Token.IdentifierToken)
        assertEquals("identifier", token.identifier)
        assertEquals(input.length, token.end)
    }

    @Test
    fun `test identifier rule does not match when input starts with digit`() {
        val input = "123abc"
        val lexer = createLexer(input)
        val token = IdentifierRule.match(lexer)
        // IdentifierRule should return null if the first character isn’t letter or underscore.
        assertNull(token, "IdentifierRule should not match when the input does not start with a letter or underscore")
    }
}