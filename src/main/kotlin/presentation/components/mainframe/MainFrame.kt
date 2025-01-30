package org.editor.presentation.components.mainframe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.editor.ApplicationScope
import org.editor.application.Caret
import org.editor.application.Document
import org.editor.application.TextEditImpl
import org.editor.presentation.components.SwingDispatchers
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

    // Single-threaded context for file operations to serialize them
    private val fileDispatcher = Dispatchers.IO.limitedParallelism(1)


    private val theme = EditorTheme()
    private val textPaneContent = TextPaneContent(TextEditImpl(Document.create()))
    private val textPane = KTextPane(textPaneContent, theme)
    private val openButton = JButton("Open File")
    private val saveButton = JButton("Save File")
    private val cursorPositionLabel = JLabel("Line: 1, Column: 1")

    // Mutex to prevent race conditions
    private val fileOperationMutex = Mutex()

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

        textPaneContent.addChangeListener { minLine, maxLine ->
            ApplicationScope.scope.launch {
                withContext(SwingDispatchers.Swing) {
                    textPane.scheduleFullRepaint()
                }
            }
        }

        // Ensure UI updates on EDT
        ApplicationScope.scope.launch {
            withContext(SwingDispatchers.Swing) {
                textPane.requestFocusInWindow()
            }
        }
    }

    private fun onOpenFile(textPane: KTextPane) {
        val fileChooser = JFileChooser()
        val result = fileChooser.showOpenDialog(this)
        if (result == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            ApplicationScope.scope.launch {
                fileOperationMutex.withLock {
                    try {
                        val fileText = readFileContent(file)
                        // Ensure UI updates on EDT
                        withContext(SwingDispatchers.Swing) {
                            textPane.setTextContent(fileText)
                            textPane.requestFocusInWindow()
                        }
                    } catch (e: Exception) {
                        withContext(SwingDispatchers.Swing) {
                            JOptionPane.showMessageDialog(
                                this@MainFrame,
                                "Error loading file: ${e.message}",
                                "Error",
                                JOptionPane.ERROR_MESSAGE
                            )
                        }
                    }
                }
            }
        }
    }

    private fun onSaveFile() {
        val fileChooser = JFileChooser()
        val result = fileChooser.showSaveDialog(this)
        if (result == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            ApplicationScope.scope.launch {
                fileOperationMutex.withLock {
                    try {
                        val content = withContext(SwingDispatchers.Swing) {
                            textPane.getTextContent()
                        }
                        writeFileContent(file, content)
                        withContext(SwingDispatchers.Swing) {
                            JOptionPane.showMessageDialog(
                                this@MainFrame,
                                "File saved successfully.",
                                "Success",
                                JOptionPane.INFORMATION_MESSAGE
                            )
                        }
                    } catch (e: Exception) {
                        withContext(SwingDispatchers.Swing) {
                            JOptionPane.showMessageDialog(
                                this@MainFrame,
                                "Error saving file: ${e.message}",
                                "Error",
                                JOptionPane.ERROR_MESSAGE
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun readFileContent(file: File): String = withContext(fileDispatcher) {
        file.readText()
    }

    private suspend fun writeFileContent(file: File, content: String) = withContext(fileDispatcher) {
        file.writeText(content)
    }

    override fun dispose() {
        super.dispose()
        ApplicationScope.dispose()
    }
}



