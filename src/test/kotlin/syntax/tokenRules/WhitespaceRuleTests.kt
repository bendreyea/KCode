package syntax.tokenRules

import org.editor.syntax.Token
import org.editor.syntax.WhitespaceRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WhitespaceRuleTests {

    @Test
    fun `test whitespace rule matches spaces and tabs`() {
        val input = "  \t  \n"
        val lexer = createLexer(input)
        val token = WhitespaceRule.match(lexer)
        assertNotNull(token, "Expected WhitespaceRule to match")
        assertTrue(token is Token.WhiteSpaceToken)
        assertEquals(input, token.text)
        assertEquals(input.length, token.end)
    }
}