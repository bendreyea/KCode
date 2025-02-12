package syntax.tokenRules

import org.editor.syntax.lexer.SingleLineCommentRule
import org.editor.syntax.lexer.Token
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SingleLineCommentRuleTests {

    @Test
    fun `test single line comment rule matches comment`() {
        val input = "// This is a comment"
        val lexer = createLexer(input)
        val token = SingleLineCommentRule.match(lexer)
        assertNotNull(token, "Expected SingleLineCommentRule to match")
        assertTrue(token is Token.CommentToken)
        assertEquals(input, token.text)
        // Since no newline is present, the token should span the entire input.
        assertEquals(input.length, token.end)
    }
}