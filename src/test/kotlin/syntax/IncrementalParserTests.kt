package syntax

import org.editor.syntax.BlockNode
import org.editor.syntax.IncrementalParser
import org.editor.syntax.TokenNode
import org.editor.syntax.TokenType
import org.editor.syntax.intervalTree.Interval
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IncrementalParserTest {

    @Test
    fun `test initial AST construction`() {
        val input = "fun main() { println(\"Hello\") }"
        val parser = IncrementalParser(input)
        val rootAST = parser.query(0)

        assertNotNull(rootAST)
        assertEquals(0, rootAST.start)
        assertEquals(input.length, rootAST.end)
    }

    @Test
    fun `test parser handles nested brackets correctly`() {
        val input = "{ [ ( ) ] }"
        val parser = IncrementalParser(input)
        val rootAST = parser.query(0)

        assertNotNull(rootAST)
        assertEquals(0, rootAST.start)
        assertEquals(input.length, rootAST.end)

        val blockNode = rootAST.children.find { it is BlockNode } as? BlockNode
        assertNotNull(blockNode)
        assertEquals(3, blockNode.children.size)
    }

    @Test
    fun `test parser handles mismatched brackets`() {
        val input = "{ ( [ ) ] }"
        val parser = IncrementalParser(input)
        val rootAST = parser.query(0)

        assertNotNull(rootAST)
        val mismatchedToken = rootAST.children.find { it is TokenNode } as? TokenNode
        assertNotNull(mismatchedToken)
        assertEquals(TokenType.SYMBOL, mismatchedToken.token.type)
    }

    @Test
    fun `test incremental edit - adding a function`() {
        val input = "fun main() { println(\"Hello\") }"
        val parser = IncrementalParser(input)

        // Perform an edit: Add another function
        val newText = "\nfun add(a: Int, b: Int) = a + b"
        val editRange = object : Interval {
            override val start = input.length
            override val end = input.length
        }
        parser.handleEdit(editRange, newText)

        val newAST = parser.query(0)
        assertNotNull(newAST)
        assertEquals(input.length + newText.length, newAST.end)
    }

    @Test
    fun `test incremental edit - modifying function body`() {
        val input = "fun main() { println(\"Hello\") }"
        val parser = IncrementalParser(input)

        // Modify the function body
        val newText = "println(\"World\")"
        val editRange = object : Interval {
            override val start = 12
            override val end = 26
        }

        parser.handleEdit(editRange, newText)

        val newAST = parser.query(0)
        assertNotNull(newAST)

        val updatedToken = parser.query(12)
        assertNotNull(updatedToken)
//        assertEquals("println(\"World\")", (updatedToken.token as Token.StringToken).value)
    }

    @Test
    fun `test AST merging retains unchanged nodes`() {
        val input = "fun main() { println(\"Hello\") }"
        val parser = IncrementalParser(input)

        val oldAST = parser.query(0)

        // Modify the function body
        val newText = "println(\"World\")"
        // Replace "println(\"Hello\")"
        val editRange = object : Interval {
            override val start = 12
            override val end = 26
        }
        parser.handleEdit(editRange, newText)

        val newAST = parser.query(0)
        assertNotNull(newAST)
        assertEquals(oldAST!!.start, newAST.start)
        assertEquals(oldAST.end + (newText.length - (editRange.end - editRange.start)), newAST.end)
    }

    @Test
    fun `test querying AST node at specific position`() {
        val input = "fun main() { println(\"Hello\") }"
        val parser = IncrementalParser(input)

        val nodeAtFun = parser.query(0)
        assertNotNull(nodeAtFun)
        assertEquals(0, nodeAtFun.start)

        val nodeAtString = parser.query(18)
        assertNotNull(nodeAtString)
        assertEquals(TokenType.STRING, (nodeAtString as TokenNode).token.type)
    }

    @Test
    fun `test querying outside range returns null`() {
        val input = "fun main() { println(\"Hello\") }"
        val parser = IncrementalParser(input)

        val outOfRangeNode = parser.query(input.length + 10)
        assertNull(outOfRangeNode)
    }
}
