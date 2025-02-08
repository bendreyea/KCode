package syntax.tokenRules

import org.editor.syntax.NumberRule
import org.editor.syntax.Token
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NumberRuleTests {
    @Test
    fun `test number rule matches digit sequence`() {
        val input = "12345"
        val lexer = createLexer(input)
        val token = NumberRule.match(lexer)
        assertNotNull(token, "Expected NumberRule to match")
        assertTrue(token is Token.NumberToken)
        assertEquals("12345", token.value)
        assertEquals(input.length, token.end)
    }
}