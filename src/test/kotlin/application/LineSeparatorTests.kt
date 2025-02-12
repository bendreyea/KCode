package application

import org.editor.application.common.LineSeparator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LineSeparatorTests {

    @Test
    fun unify_withLf_unifiesLineEndingsToLf() {
        val text = "Line1\r\nLine2\rLine3\nLine4"
        val result = LineSeparator.LF.unify(text)
        assertEquals("Line1\nLine2\nLine3\nLine4", result)
    }

    @Test
    fun unify_withCr_unifiesLineEndingsToCr() {
        val text = "Line1\r\nLine2\rLine3\nLine4"
        val result = LineSeparator.CR.unify(text)
        assertEquals("Line1\rLine2\rLine3\rLine4", result)
    }

    @Test
    fun unify_withCrLf_unifiesLineEndingsToCrLf() {
        val text = "Line1\r\nLine2\rLine3\nLine4"
        val result = LineSeparator.CRLF.unify(text)
        assertEquals("Line1\r\nLine2\r\nLine3\r\nLine4", result)
    }

    @Test
    fun str_withLf_returnsCorrectString() {
        assertEquals("\n", LineSeparator.LF.str())
    }

    @Test
    fun str_withCr_returnsCorrectString() {
        assertEquals("\r", LineSeparator.CR.str())
    }

    @Test
    fun str_withCrLf_returnsCorrectString() {
        assertEquals("\r\n", LineSeparator.CRLF.str())
    }

    @Test
    fun estimate_withEqualCrAndLf_returnsCrLf() {
        assertEquals(LineSeparator.CRLF, LineSeparator.estimate(5, 5))
    }

    @Test
    fun estimate_withOnlyCr_returnsCr() {
        assertEquals(LineSeparator.CR, LineSeparator.estimate(5, 0))
    }

    @Test
    fun estimate_withOnlyLf_returnsLf() {
        assertEquals(LineSeparator.LF, LineSeparator.estimate(0, 5))
    }

    @Test
    fun estimate_withNoCrOrLf_returnsPlatformNewLine() {
        assertEquals(LineSeparator.platform, LineSeparator.estimate(0, 0))
    }

    @Test
    fun determinePlatformNewLine_returnsCorrectNewLine() {
        val expected = when (System.lineSeparator()) {
            "\r" -> LineSeparator.CR
            "\r\n" -> LineSeparator.CRLF
            else -> LineSeparator.LF
        }
        assertEquals(expected, LineSeparator.platform)
    }
}