package syntax.tokenRules

import org.editor.syntax.lexer.BracketInfo
import org.editor.syntax.lexer.BracketRule
import org.editor.syntax.lexer.Token
import org.editor.syntax.lexer.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BracketRuleTests {
    @Test
    fun `test bracket rule matches open bracket`() {
        val input = "("
        val lexer = createLexer(input)
        // At this point the bracket stack is empty.
        val token = BracketRule.match(lexer)
        assertNotNull(token, "Expected BracketRule to match open bracket")
        assertTrue(token is Token.BracketToken)
        // Check that the bracket token records the correct character.
        assertEquals('(', token.symbol)
        // The color (or nesting level) for an open bracket in an empty stack should be 0.
        assertEquals(0, token.level)
        // The token’s type should be BRACKET_LEVEL_0.
        assertEquals(TokenType.BRACKET_LEVEL_0, token.type)
    }

    @Test
    fun `test bracket rule matches closing bracket with matching open`() {
        val input = ")"
        val lexer = createLexer(input)
        // Simulate that an open bracket was seen before.
        // For this test we simulate that the open bracket had a colorIndex of 1.
        lexer.bracketStack.addLast(BracketInfo('(', 0, 1, 1))
        val token = BracketRule.match(lexer)
        assertNotNull(token, "Expected BracketRule to match closing bracket")
        assertTrue(token is Token.BracketToken)
        // The token should have the matching level from the open bracket.
        assertEquals(1, token.level)
        // The token’s type should be BRACKET_LEVEL_1.
        assertEquals(TokenType.BRACKET_LEVEL_1, token.type)
    }
}