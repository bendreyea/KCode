package application

import org.editor.application.NewLine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NewLineTests {

    @Test
    fun unify_withLf_unifiesLineEndingsToLf() {
        val text = "Line1\r\nLine2\rLine3\nLine4"
        val result = NewLine.LF.unify(text)
        assertEquals("Line1\nLine2\nLine3\nLine4", result)
    }

    @Test
    fun unify_withCr_unifiesLineEndingsToCr() {
        val text = "Line1\r\nLine2\rLine3\nLine4"
        val result = NewLine.CR.unify(text)
        assertEquals("Line1\rLine2\rLine3\rLine4", result)
    }

    @Test
    fun unify_withCrLf_unifiesLineEndingsToCrLf() {
        val text = "Line1\r\nLine2\rLine3\nLine4"
        val result = NewLine.CRLF.unify(text)
        assertEquals("Line1\r\nLine2\r\nLine3\r\nLine4", result)
    }

    @Test
    fun str_withLf_returnsCorrectString() {
        assertEquals("\n", NewLine.LF.str())
    }

    @Test
    fun str_withCr_returnsCorrectString() {
        assertEquals("\r", NewLine.CR.str())
    }

    @Test
    fun str_withCrLf_returnsCorrectString() {
        assertEquals("\r\n", NewLine.CRLF.str())
    }

    @Test
    fun estimate_withEqualCrAndLf_returnsCrLf() {
        assertEquals(NewLine.CRLF, NewLine.estimate(5, 5))
    }

    @Test
    fun estimate_withOnlyCr_returnsCr() {
        assertEquals(NewLine.CR, NewLine.estimate(5, 0))
    }

    @Test
    fun estimate_withOnlyLf_returnsLf() {
        assertEquals(NewLine.LF, NewLine.estimate(0, 5))
    }

    @Test
    fun estimate_withNoCrOrLf_returnsPlatformNewLine() {
        assertEquals(NewLine.platform, NewLine.estimate(0, 0))
    }

    @Test
    fun determinePlatformNewLine_returnsCorrectNewLine() {
        val expected = when (System.lineSeparator()) {
            "\r" -> NewLine.CR
            "\r\n" -> NewLine.CRLF
            else -> NewLine.LF
        }
        assertEquals(expected, NewLine.platform)
    }
}