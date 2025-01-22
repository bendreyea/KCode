package org.editor.presentation.components.mainframe

import kotlinx.coroutines.*
import org.editor.application.Caret
import org.editor.application.Document
import org.editor.application.TextEditImpl
import org.editor.presentation.components.scrollpane.KScrollPane
import org.editor.presentation.components.textpane.CaretListener
import org.editor.presentation.components.textpane.EditorTheme
import org.editor.presentation.components.textpane.KTextPane
import org.editor.presentation.components.textpane.TextPaneContent
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.io.File
import javax.swing.*

/**
 * The main frame of the application.
 */
class MainFrame : JFrame("KCode") {

    // If you want coroutines:
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val theme = EditorTheme()
    private val textPaneContent = TextPaneContent(TextEditImpl(Document.create()), mainScope)
    private val textPane = KTextPane(textPaneContent, theme)
    private val openButton = JButton("Open File")
    private val saveButton = JButton("Save File")
    private val cursorPositionLabel = JLabel("Line: 1, Column: 1")


    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        size = Dimension(800, 600)
        layout = BorderLayout()

        val buttonPanel = JPanel().apply {
            layout = FlowLayout(FlowLayout.LEFT)
            add(openButton)
            add(saveButton)
        }

        val scrollPane = KScrollPane(textPane, theme)
        add(scrollPane, BorderLayout.CENTER)
        add(buttonPanel, BorderLayout.NORTH)
        add(cursorPositionLabel, BorderLayout.SOUTH)

        openButton.addActionListener { onOpenFile(textPane) }
        saveButton.addActionListener { onSaveFile() }

        textPane.addCaretListener(object : CaretListener {
            override fun caretMoved(newCaret: Caret) {
                cursorPositionLabel.text = "Line: ${newCaret.row + 1}, Column: ${newCaret.col + 1}"
            }
        })

        textPaneContent.addChangeListener({ minLine, maxLine ->
            textPane.scheduleFullRepaint()
        })

        SwingUtilities.invokeLater {
            textPane.requestFocusInWindow()
        }
    }

    private fun onOpenFile(textPane: KTextPane) {
        val fileChooser = JFileChooser()
        val result = fileChooser.showOpenDialog(this)
        if (result == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            mainScope.launch {
                val fileText = readFileContent(file)
                SwingUtilities.invokeLater {
                    textPane.setTextContent(fileText)
                    textPane.requestFocusInWindow()
                }
            }
        }
    }

    private fun onSaveFile() {
        val fileChooser = JFileChooser()
        val result = fileChooser.showSaveDialog(this)
        if (result == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            mainScope.launch {
                val content = textPane.getTextContent()
                writeFileContent(file, content)
            }
        }
    }

    private suspend fun readFileContent(file: File): String = withContext(Dispatchers.IO) {
        file.readText()
    }

    private suspend fun writeFileContent(file: File, content: String) = withContext(Dispatchers.IO) {
        file.writeText(content)
    }
}


