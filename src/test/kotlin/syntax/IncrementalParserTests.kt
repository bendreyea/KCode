package syntax

import org.editor.application.doc.DocumentSnapshot
import org.editor.application.editor.EditorDocumentSnapshot
import org.editor.application.TextRowIndex
import org.editor.application.RowIndex
import org.editor.core.PieceTable
import org.editor.syntax.highlighter.*
import org.editor.syntax.lexer.BracketInfo
import org.editor.syntax.lexer.LexerState
import org.editor.syntax.lexer.Token
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class IncrementalParserTest {

    private lateinit var parser: Parser

    @BeforeTest
    fun setUp() {
        parser = Parser()
    }

//    @Test
//    fun testInitializeCheckpoints() {
//        parser.resizeCheckpoints(300)
//        assertEquals(15, parser.chunkSize)
//    }
//
//    @Test
//    fun testGetAllIntervalsWithNoAST() {
//        val intervals = parser.getAllIntervals(0)
//        assertTrue(intervals.isEmpty())
//    }

//    @Test
//    fun testParseLineIfNeededWithCache() {
//        val lineText = "int a = 10;"
//        val state = LexerState.DEFAULT
//        val bracketStack = ArrayDeque<BracketInfo>()
//        parser.parseLineIfNeeded(1, { lineText }, state, bracketStack)
//        assertNotNull(parser.parseLineIfNeeded(1, { lineText }, state, bracketStack))
//    }
//
//    @Test
//    fun testParseIfNeeded() {
//        val pt = PieceTable.create();
//        pt.insert(0, "int a = 10;".toByteArray())
//        val dryBuffer = mutableMapOf<Int, String>()
//        val index = RowIndex.create();
//        val doc = DocumentSnapshot.create(pt, index)
//        val document = EditorDocumentSnapshot(doc, dryBuffer)
//
//        parser.resizeCheckpoints(100)
//        parser.handleEdit(HighlightInterval(0, 50, TokenType.KEYWORD), document)
//        assertNotNull(parser.getAllIntervals(0))
//    }

    @Test
    fun testMergeASTWithSameNodes() {
        val node = TokenNode(Token.KeywordToken( 0, 5, "class"), 0, 5)
        val merged = node.mergeWith(node)
        assertSame(node, merged)
    }

    @Test
    fun testMergeASTWithDifferentNodes() {
        val node1 = TokenNode(Token.KeywordToken( 0, 5, "class"), 0, 5)
        val node2 = TokenNode(Token.IdentifierToken(0, 7, "MyClass"), 0, 7)
        val merged = node1.mergeWith(node2)
        assertNotNull(merged)
    }

    /*
BlockNode (Level: 0)
├── TokenNode (KEYWORD) [0, 6]  // public
├── TokenNode (KEYWORD) [7, 12]  // class
├── TokenNode (IDENTIFIER) [13, 23]  // HelloWorld
├── BlockNode (Bracket: {) (Level: 1)
│   ├── TokenNode (KEYWORD) [24, 30]  // public
│   ├── TokenNode (KEYWORD) [31, 37]  // static
│   ├── TokenNode (KEYWORD) [38, 42]  // void
│   ├── TokenNode (IDENTIFIER) [43, 47]  // main
│   ├── BlockNode (Bracket: () ) (Level: 2)
│   │   ├── TokenNode (IDENTIFIER) [48, 56]  // String[]
│   │   ├── TokenNode (IDENTIFIER) [57, 61]  // args
│   ├── BlockNode (Bracket: {) (Level: 2)
│   │   ├── TokenNode (IDENTIFIER) [62, 68]  // System
│   │   ├── TokenNode (SYMBOL) [69, 70]  // .
│   │   ├── TokenNode (IDENTIFIER) [71, 74]  // out
│   │   ├── TokenNode (SYMBOL) [75, 76]  // .
│   │   ├── TokenNode (IDENTIFIER) [77, 84]  // println
│   │   ├── BlockNode (Bracket: () ) (Level: 3)
│   │   │   ├── TokenNode (STRING) [85, 100]  // "Hello, World!"
│   │   ├── TokenNode (SYMBOL) [101, 102]  // ;
│   ├── TokenNode (SYMBOL) [103, 104]  // }
├── TokenNode (SYMBOL) [105, 106]  // }
     */
    @Test
    fun testIntervalTree() {
        val pt = PieceTable.create();
        val dryBuffer = mutableMapOf<Int, String>()
        val index = RowIndex.create()
        val stIndex = TextRowIndex.create()
        val text = """
            public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello, World!");
                }
            }
        """.trimIndent()

        val textByte = text.toByteArray()
        pt.insert(0, textByte)
        index.insert(0, 0, textByte)
        stIndex.insert(0, 0, text)
        val doc = DocumentSnapshot.create(pt, index)
        val document = EditorDocumentSnapshot(1, stIndex, doc, dryBuffer)
        var root: ASTNode? = null
        var lexerState = LexerState.DEFAULT
        var deque = ArrayDeque<BracketInfo>()
        for (i in 0 until doc.rows()) {
            val start = document.serial(i, 0)
            val end = document.serial(i, document.getText(i).length)
            val result = parser.parseLine(document.getText(i), start, end, lexerState, deque)
            lexerState = result.endState
            deque = result.newBracketStack
            root = root.mergeWith(result.ast)
        }

        root!!.print()
    }

}

