package syntax.tokenRules

import org.editor.syntax.BlockCommentRule
import org.editor.syntax.Token
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BlockCommentRuleTests {

    @Test
    fun `test block comment rule matches comment`() {
        val input = "/* Block comment */"
        val lexer = createLexer(input)
        val token = BlockCommentRule.match(lexer)
        assertNotNull(token, "Expected BlockCommentRule to match")
        assertTrue(token is Token.CommentToken)
        assertEquals(input, token.text)
        assertEquals(input.length, token.end)
    }
}