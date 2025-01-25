package presentation

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.editor.application.Caret
import org.editor.presentation.components.textpane.EditorTheme
import org.editor.presentation.components.textpane.KTextEditorController
import org.editor.presentation.components.textpane.TextPaneContent
import java.awt.FontMetrics
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JPanel
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KTextEditorControllerTest {

    private lateinit var controller: KTextEditorController
    private lateinit var textPane: TextPaneContent
    private lateinit var fontMetrics: FontMetrics
    private lateinit var editorTheme: EditorTheme

    @BeforeTest
    fun setup() {
        textPane = mockk()
        fontMetrics = mockk()
        editorTheme = mockk()

        every { fontMetrics.height } returns 16
        every { fontMetrics.charWidth(any<Char>()) } answers { 8 }
        every { editorTheme.gutterWidth } returns 10
        every { editorTheme.horizontalPadding } returns 5
        every { textPane.rows() } returns 10
        every { textPane.getText(any()) } returns "Sample line"
        every { textPane.getText(any(), any()) } returns ""

        controller = KTextEditorController(
            textPane,
            fontMetrics,
            editorTheme,
            getHeight = { 800 },
            getWidth = { 600 },
            repaintCallback = { _, _ -> },
            repaintVisibleRegion = {}
        )
    }

    @Test
    fun keyPressed_handleCopyOperation() {
        // Arrange
        controller.selectionStart = Caret(0, 0)
        controller.selectionEnd = Caret(0, 5)
        every { textPane.getText(Caret(0, 0), Caret(0, 5)) } returns "Hello"

        val keyEvent = KeyEvent(
            JPanel(),
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            KeyEvent.CTRL_DOWN_MASK,
            KeyEvent.VK_C,
            'C'
        )

        // Act
        controller.keyPressed(keyEvent)

        // Assert
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val copiedText = clipboard.getData(DataFlavor.stringFlavor) as String
        assertEquals("Hello", copiedText)
    }

    @Test
    fun keyPressed_handlePasteOperation() {
        // Arrange
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection("PastedText"), null)

        every { textPane.insert(any(), any(), any()) } returns Caret(0, 10)
        every { textPane.rows() } returns 1

        val keyEvent = KeyEvent(
            JPanel(),
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            KeyEvent.CTRL_DOWN_MASK,
            KeyEvent.VK_V,
            'V'
        )

        // Act
        controller.keyPressed(keyEvent)

        // Assert
        verify { textPane.insert(0, 0, "PastedText") }
    }

    @Test
    fun keyPressed_handleKeyMovement() {
        // Arrange
        val keyEvent = KeyEvent(
            JPanel(),
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            0,
            KeyEvent.VK_RIGHT,
            KeyEvent.CHAR_UNDEFINED
        )

        // Act
        controller.keyPressed(keyEvent)

        // Assert
        assertEquals(Caret(0, 1), controller.caret)
    }

    @Test
    fun mousePressed_handleMouseSelection() {
        // Arrange
        val mouseEvent = MouseEvent(
            JPanel(),
            MouseEvent.MOUSE_PRESSED,
            System.currentTimeMillis(),
            0,
            20,
            30,
            1,
            false
        )

        // Act
        controller.mousePressed(mouseEvent)

        // Assert
        assertEquals(controller.caret, controller.selectionStart)
    }

    @Test
    fun keyPressed_handleDeleteSelection() {
        // Arrange
        controller.selectionStart = Caret(0, 0)
        controller.selectionEnd = Caret(0, 5)
        every { textPane.getText(Caret(0, 0), Caret(0, 5)) } returns "Hello"
        every { textPane.replace(any(), any(), any(), any()) } answers { Caret(0, 0) }

        val keyEvent = KeyEvent(
            JPanel(),
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            0,
            KeyEvent.VK_DELETE,
            KeyEvent.CHAR_UNDEFINED
        )

        // Act
        controller.keyPressed(keyEvent)

        // Assert
        assertEquals(Caret(0, 0), controller.caret)
        verify { textPane.replace(0, 0, 5, "") }
    }

    @Test
    fun mouseDragged_updateSelection() {
        // Arrange
        controller.selectionStart = Caret(0, 0)
        val mouseEvent = MouseEvent(
            JPanel(),
            MouseEvent.MOUSE_DRAGGED,
            System.currentTimeMillis(),
            0,
            100,
            20,
            1,
            false
        )

        every { textPane.getText(0) } returns "Sample line"
        every { textPane.rows() } returns 10

        // Act
        controller.mouseDragged(mouseEvent)

        // Assert
        assertEquals(controller.selectionStart, Caret(0, 0))
        assertEquals(controller.selectionEnd, controller.caret)
    }

    @Test
    fun keyTyped_insertCharacter() {
        // Arrange
        controller.caret = Caret(0, 5)
        every { textPane.insert(0, 5, "a") } returns Caret(0, 6)

        val keyEvent = KeyEvent(
            JPanel(),
            KeyEvent.KEY_TYPED,
            System.currentTimeMillis(),
            0,
            0,
            'a'
        )

        // Act
        controller.keyTyped(keyEvent)

        // Assert
        assertEquals(Caret(0, 6), controller.caret)
        verify { textPane.insert(0, 5, "a") }
    }

    @Test
    fun keyPressed_pageDownMovesCaret() {
        // Arrange
        val keyEvent = KeyEvent(
            JPanel(),
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            0,
            KeyEvent.VK_PAGE_DOWN,
            KeyEvent.CHAR_UNDEFINED
        )
        every { textPane.rows() } returns 20

        // Act
        controller.keyPressed(keyEvent)

        // Assert
        assertEquals(Caret(5, 0), controller.caret)
    }

    @Test
    fun getSelectedText_returnsSelectedText() {
        // Arrange
        controller.selectionStart = Caret(0, 0)
        controller.selectionEnd = Caret(0, 5)
        every { textPane.getText(Caret(0, 0), Caret(0, 5)) } returns "Hello"

        // Act
        val selectedText = controller.getSelectedText()

        // Assert
        assertEquals("Hello", selectedText)
    }
}
