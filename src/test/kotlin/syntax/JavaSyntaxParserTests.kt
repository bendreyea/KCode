package syntax

import org.editor.syntax.SyntaxHighlighter
import org.editor.syntax.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JavaSyntaxParserTests {

    @Test
    fun onLineChanged_parsesSingleLineCorrectly() {
        val parser = SyntaxHighlighter()
        parser.initializeCheckpoints(2)
        parser.onLineChanged(0, { line ->
            when (line) {
                0 -> "public class Main {"
                1 -> "}"
                else -> ""
            }
        }, { 2 })

        val intervals = parser.getAllIntervals(0)
        assertEquals(7, intervals.size)
        assertEquals(TokenType.KEYWORD, intervals[0].type)
        assertEquals(TokenType.WHITESPACE, intervals[1].type)
        assertEquals(TokenType.KEYWORD, intervals[2].type)
    }

    @Test
    fun onLineChanged_parsesMultiLineCorrectly() {
        val parser = SyntaxHighlighter()
        parser.initializeCheckpoints(2)
        parser.onLineChanged(0, { line ->
                when (line) {
                    0 -> "public class Main {"
                    1 -> "}"
                    else -> ""
                }

        }, {2})
        val intervalsLine0 = parser.getAllIntervals(0)
        val intervalsLine1 = parser.getAllIntervals(1)
        assertEquals(7, intervalsLine0.size)
        assertEquals(1, intervalsLine1.size)
    }

    @Test
    fun onLineChanged_handlesBlockComments() {
        val parser = SyntaxHighlighter()
        parser.initializeCheckpoints(2)
        parser.onLineChanged(0, { line ->
            when (line) {
                0 -> "/* comment */"
                else -> ""
            }
        }, { 1 })

        val intervals = parser.getAllIntervals(0)
        assertEquals(1, intervals.size)
        assertEquals(TokenType.COMMENT, intervals[0].type)
    }

    @Test
    fun onLineChanged_handlesStringLiterals() {
        val parser = SyntaxHighlighter()
        parser.initializeCheckpoints(2)
        parser.onLineChanged(0, { line ->
            when (line) {
                0 -> "String s = \"Hello\";"
                else -> ""
            }
        }, { 1 })

        val intervals = parser.getAllIntervals(0)
        assertEquals(8, intervals.size)
        assertEquals(TokenType.STRING, intervals[6].type)
    }

    @Test
    fun onLineChanged_handlesNestedBrackets() {
        val parser = SyntaxHighlighter()
        parser.initializeCheckpoints(2)
        parser.onLineChanged(0, { line ->
            when (line) {
                0 -> "public void method() { if (true) { } }"
                else -> ""
            }
        }, { 1 })

        val intervals = parser.getAllIntervals(0)
        assertEquals(21, intervals.size)
        assertEquals(TokenType.BRACKET_LEVEL_0, intervals[5].type)
        assertEquals(TokenType.BRACKET_LEVEL_1, intervals[12].type)
    }

    @Test
    fun onLineChanged_parsesIncrementally() {
        val parser = SyntaxHighlighter()
        parser.initializeCheckpoints(2)
        parser.onLineChanged(0, { line ->
            when (line) {
                0 -> "public class Main {"
                1 -> "}"
                else -> ""
            }
        }, { 2 })

        val intervalsLine = parser.getAllIntervals(0)
        assertEquals(7, intervalsLine.size)
    }

    @Test
    fun onLineChanged_handlesNestedBlockComments() {
        val parser = SyntaxHighlighter()
        parser.initializeCheckpoints(2)
        parser.onLineChanged(0, { line ->
            when (line) {
                0 -> "/* This is /* a nested */ comment */"
                else -> ""
            }
        }, { 2 })
        val intervals = parser.getAllIntervals(0)
        // Expect the entire line to be a single comment
        assertEquals(6, intervals.size)
        assertEquals(TokenType.COMMENT, intervals[0].type)
    }

    @Test
    fun onLineChanged_handlesSingleLineCommentsAfterCode() {
        val parser = SyntaxHighlighter()
        parser.initializeCheckpoints(2)
        parser.onLineChanged(0, { line ->
            when (line) {
                0 -> "int x = 10; // initialize x"
                else -> ""
            }
        }, { 2 })

        val intervals = parser.getAllIntervals(0)
        // Expect tokens for 'int', whitespace, 'x', whitespace, '=', whitespace, '10', ';', whitespace, comment
        assertTrue(intervals.any { it.type == TokenType.KEYWORD && it.start == 0 })
        assertTrue(intervals.any { it.type == TokenType.COMMENT })
    }

    @Test
    fun onLineChanged_handlesIdentifiersWithUnderscoresAndDigits() {
        val parser = SyntaxHighlighter()
        parser.initializeCheckpoints(2)
        parser.onLineChanged(0, { line ->
            when (line) {
                0 -> "int _value1 = 42;"
                else -> ""
            }
        }, { 2 })

        val intervals = parser.getAllIntervals(0)
        // Expect IDENTIFIER tokens for '_value1' and NUMBER token for '42'
        val identifier = intervals.find { it.type == TokenType.IDENTIFIER && it.start == 4 }
        assertTrue(identifier != null)
        val number = intervals.find { it.type == TokenType.NUMBER }
        assertTrue(number != null)
        assertEquals("42", "_value1 = 42;".substring(number.start - 4, number.end - 4))
    }

    @Test
    fun onLineChanged_handlesUnmatchedOpenBrackets() {
        val parser = SyntaxHighlighter()
        parser.initializeCheckpoints(2)
        parser.onLineChanged(0, { line ->
            when (line) {
                0 -> "public void method() { if (true) { "
                else -> ""
            }
        }, { 2 })

        val intervals = parser.getAllIntervals(0)
        val bracketLevel0 = intervals.filter { it.type == TokenType.BRACKET_LEVEL_0 }
        val bracketLevel1 = intervals.filter { it.type == TokenType.BRACKET_LEVEL_1 }
        assertEquals(3, bracketLevel0.size)
        assertEquals(3, bracketLevel1.size)
    }

    @Test
    fun onLineChanged_handlesBrackets() {
        val parser = SyntaxHighlighter()
        parser.initializeCheckpoints(2)
        parser.onLineChanged(0, { line ->
            when (line) {
                0 -> "public TextEditImpl(Document doc) {"
                1 -> "this.doc = doc; }"
                else -> ""
            }
        }, { 2 })

        val intervalsLine0 = parser.getAllIntervals(0)

        // Line 0 should have tokens
        assertTrue(intervalsLine0.isNotEmpty())

        assertEquals(10, intervalsLine0.size)
        assertEquals(TokenType.BRACKET_LEVEL_0, intervalsLine0[intervalsLine0.size - 1].type)
    }

    @Test
    fun parseLineDocument_handlesBrackets() {
        val parser = SyntaxHighlighter()
        parser.initializeCheckpoints(2)
        parser.onLineChanged(0, { line ->
            when (line) {
                0 -> "{"
                1 -> "}"
                else -> ""
            }
        }, { 2 })

        val intervalsLine0 = parser.getAllIntervals(0)

        // Line 0 should have tokens
        assertTrue(intervalsLine0.isNotEmpty())
        assertEquals(TokenType.BRACKET_LEVEL_0, intervalsLine0[0].type)
    }

    @Test
    fun parseDocument_handlesMultipleStringLiteralsOnSameLine() {
        val parser = SyntaxHighlighter()
        parser.initializeCheckpoints(2)
        parser.onLineChanged(0, { line ->
            when (line) {
                0 -> "String s1 = \"Hello\", s2 = \"World\";"
                else -> ""
            }
        }, { 2 })

        val intervals = parser.getAllIntervals(0)
        // Expect two STRING tokens
        val stringTokens = intervals.filter { it.type == TokenType.STRING }
        assertEquals(2, stringTokens.size)
    }

    @Test
    fun parseDocument_handlesEmptyLines() {
        val parser = SyntaxHighlighter()
        parser.initializeCheckpoints(3)
        parser.onLineChanged(0, { line ->
            when (line) {
                0 -> "public class Test {}"
                1 -> ""
                2 -> "    "
                else -> ""
            }
        }, {3})

        val intervalsLine0 = parser.getAllIntervals(0)
        val intervalsLine1 = parser.getAllIntervals(1)
        val intervalsLine2 = parser.getAllIntervals(2)

        // Line 0 should have tokens
        assertTrue(intervalsLine0.isNotEmpty())

        // Line 1 is completely empty
        assertTrue(intervalsLine1.isEmpty())

        // Line 2 has only whitespace
        assertEquals(1, intervalsLine2.size)
        assertEquals(TokenType.WHITESPACE, intervalsLine2[0].type)
    }

    @Test
    fun parseDocument_handlesLinesWithOnlyWhitespace() {
        val parser = SyntaxHighlighter()
        parser.initializeCheckpoints(2)
        parser.onLineChanged(0, { line ->
            when (line) {
                0 -> "    \t   "
                else -> ""
            }
        }, { 2 })

        val intervals = parser.getAllIntervals(0)
        // Entire line should be whitespace
        assertEquals(1, intervals.size)
        assertEquals(TokenType.WHITESPACE, intervals[0].type)
    }

    @Test
    fun parseDocument_handlesSymbolsAndOperators() {
        val parser = SyntaxHighlighter()
        parser.initializeCheckpoints(2)
        parser.onLineChanged(0, { line ->
            when (line) {
                0 -> "a += b * (c - d) / e;"
                else -> ""
            }
        }, { 2 })

        val intervals = parser.getAllIntervals(0)
        // Expect SYMBOL tokens for operators and brackets
        val symbolTokens = intervals.filter { it.type == TokenType.SYMBOL }
        val bracketTokens = intervals.filter { it.type == TokenType.BRACKET_LEVEL_0 || it.type == TokenType.BRACKET_LEVEL_1 || it.type == TokenType.BRACKET_LEVEL_2 }
        assertTrue(symbolTokens.size >= 5) // +=, *, -, /, ;
        assertTrue(bracketTokens.size == 2) // ( and )
    }

    @Test
    fun parseDocument_handlesKeywordsAdjacentToIdentifiers() {
        val parser = SyntaxHighlighter()
        parser.initializeCheckpoints(2)
        parser.onLineChanged(0, { line ->
            when (line) {
                0 -> "int integerValue = 5;"
                else -> ""
            }
        }, { 2 })

        val intervals = parser.getAllIntervals(0)
        // 'int' should be KEYWORD, 'integerValue' should be IDENTIFIER
        val keyword = intervals.find { it.type == TokenType.KEYWORD && it.start == 0 }
        val identifier = intervals.find { it.type == TokenType.IDENTIFIER && it.start == 4 }
        assertTrue(keyword != null)
        assertTrue(identifier != null)
    }

    @Test
    fun parseDocument_handlesMultipleBlockComments() {
        val parser = SyntaxHighlighter()
        parser.initializeCheckpoints(2)
        parser.onLineChanged(0, { line ->
            when (line) {
                0 -> "/* Comment 1 */ int x = 0; /* Comment 2 */"
                else -> ""
            }
        }, { 2 })

        val intervals = parser.getAllIntervals(0)
        // Expect two COMMENT tokens and tokens for 'int', 'x', '=', '0', ';'
        val commentTokens = intervals.filter { it.type == TokenType.COMMENT }
        assertEquals(2, commentTokens.size)
        val keyword = intervals.find { it.type == TokenType.KEYWORD && it.start == "/* Comment 1 */ ".length }
        assertTrue(keyword != null)
    }

    @Test
    fun parseDocument_handlesKeywordAsIdentifier() {
        val parser = SyntaxHighlighter()
        parser.initializeCheckpoints(2)
        parser.onLineChanged(0, { line ->
            when (line) {
                0 -> "int class = 10;"
                else -> ""
            }
        }, { 2 })

        val intervals = parser.getAllIntervals(0)
        // 'int' should be KEYWORD, 'classValue' should be IDENTIFIER
        val keyword = intervals.find { it.type == TokenType.KEYWORD && it.start == 0 }
        val identifier = intervals.find { it.type == TokenType.KEYWORD && it.start == 4 }
        assertTrue(keyword != null)
        assertTrue(identifier != null)
    }
}