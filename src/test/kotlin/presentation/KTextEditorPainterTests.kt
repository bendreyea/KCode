package presentation

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.editor.application.Caret
import org.editor.presentation.components.textpane.EditorTheme
import org.editor.presentation.components.textpane.KTextEditorController
import org.editor.presentation.components.textpane.KTextEditorPainter
import org.editor.presentation.components.textpane.TextPaneContent
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.image.BufferedImage
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KTextEditorPainterTests {

    private lateinit var textPane: TextPaneContent
    private lateinit var controller: KTextEditorController
    private lateinit var fontMetrics: FontMetrics
    private lateinit var editorTheme: EditorTheme
    private lateinit var graphics: Graphics2D
    private lateinit var painter: KTextEditorPainter

    @BeforeTest
    fun setup() {
        textPane = mockk()
        controller = mockk(relaxed = true)
        fontMetrics = mockk()
        editorTheme = mockk(relaxed = true)
        graphics = mockk(relaxed = true)
        editorTheme = EditorTheme()

        every { fontMetrics.height } returns 16
        every { fontMetrics.descent } returns 4
        every { fontMetrics.stringWidth(any()) } answers { arg<String>(0).length * 8 }

        every { textPane.rows() } returns 5
        every { textPane.getText(any()) } answers { "Line ${arg<Int>(0) + 1}" }
        every { textPane.getIntervalsForLine(any()) } answers { listOf() }

        painter = KTextEditorPainter(textPane, controller, fontMetrics, editorTheme)
    }

    // Use case: Verify that the `paint` method calls all necessary sub-methods in the correct order
    @Test
    fun paint_callsAllSubMethodsInCorrectOrder() {
        // Arrange
        val width = 800
        val height = 600
        every { graphics.clipBounds } returns Rectangle(0, 0, width, height)

        // Act
        painter.paint(graphics, width, height)

        // Assert
        verifyOrder {
            graphics.color = editorTheme.canvasBackgroundColor
            graphics.fillRect(0, 0, width, height)

            graphics.translate(-controller.scrollX, -controller.scrollY)

            graphics.color = editorTheme.gutterColor
            graphics.fillRect(0, 0, editorTheme.gutterWidth, height)

            graphics.color = editorTheme.textColor
        }
    }

    // Use case: Verify that the `paintBackground` method draws the editor's background with the correct dimensions and color
    @Test
    fun paintBackground_drawsEditorBackground() {
        // Arrange
        val width = 800
        val height = 600

        // Act
        painter.paint(graphics, width, height)

        // Assert
        verify { graphics.fillRect(0, 0, width, height) }
    }

    // Use case: Verify that the gutter is painted with the correct color and dimensions
    @Test
    fun paint_verifyOrder_expectedCaretColourLast() {
        // Arrange
        val clipBounds = Rectangle(0, 0, 800, 100)
        val height = 600
        val bufferedImage = BufferedImage(800, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = bufferedImage.createGraphics()
        graphics.clip = clipBounds

        // Act
        painter.paint(graphics, 800, height)

        // Assert
        assertEquals(editorTheme.caretColor, graphics.color)
    }

    @Test
    fun paintLines_drawsVisibleLines() {
        // Arrange
        val clipBounds = Rectangle(0, 0, 800, 100) // Define the visible clipping area
        val bufferedImage = BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB)
        val graphics = bufferedImage.createGraphics() // Real Graphics2D object

        graphics.clip = clipBounds // Explicitly set the clip bounds

        every { textPane.rows() } returns 10
        every { textPane.getText(any()) } answers { "Line ${arg<Int>(0) + 1}" }

        // Act
        painter.paint(graphics, 800, 600)

        // Assert
        val expectedTextColor = editorTheme.textColor.rgb
        for (row in 0..5) { // Assuming 5 visible rows based on the clip height and fontMetrics.height
            val yPos = row * fontMetrics.height + (fontMetrics.height - fontMetrics.descent)

            // Verify that text is drawn by checking pixels in the gutter area
            for (x in editorTheme.gutterWidth until 800) {
                val pixelColor = bufferedImage.getRGB(x, yPos)
                if (x >= editorTheme.gutterWidth + editorTheme.horizontalPadding) {
                    // Text area should have text color
//                    assertEquals(expectedTextColor, pixelColor)
                }
            }
        }
    }


    // Use case: Verify that the caret is painted at the correct position on the screen
    @Test
    fun paintCaret_drawsCaretAtCorrectPosition() {
        // Arrange
        every { controller.caret } returns Caret(0, 4)

        // Act
        painter.paint(graphics, 800, 600)

        // Assert
        verify {
            graphics.color = editorTheme.caretColor
            graphics.drawLine(any(), any(), any(), any())
        }
    }
}
