package org.editor.presentation.components.mainframe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.editor.ApplicationScope
import org.editor.SwingDispatchers
import org.editor.application.doc.DocumentImpl
import org.editor.application.editor.TextEditImpl
import org.editor.application.common.UserCaret
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
    private val textPaneContent = TextPaneContent(TextEditImpl(DocumentImpl.create()))
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
            override fun caretMoved(newCaret: UserCaret) {
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
                    val fileText = readFileContent(file)
                    // Ensure UI updates on EDT
                    withContext(SwingDispatchers.Swing) {
                        textPane.setTextContent(fileText)
                        textPane.requestFocusInWindow()
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
                    } catch (e: Exception) {
                        e.printStackTrace()
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

//
//import kotlinx.coroutines.*
//import kotlinx.coroutines.swing.SwingDispatcher
//import javax.swing.*
//import javax.swing.event.DocumentEvent
//import javax.swing.event.DocumentListener
//import java.awt.Color
//import java.util.concurrent.ConcurrentLinkedQueue
//
//// Reusable buffer pool for text snapshots
//object CharBufferPool {
//    private const val MAX_POOL_SIZE = 5
//    private val pool = ConcurrentLinkedQueue<CharArray>()
//
//    fun acquire(size: Int): CharArray {
//        return pool.poll()?.takeIf { it.size >= size }?.also {
//            it.fill('\u0000')
//        } ?: CharArray(size)
//    }
//
//    fun release(buffer: CharArray) {
//        if (pool.size < MAX_POOL_SIZE) pool.offer(buffer)
//    }
//}
//
//fun main() {
//    SwingUtilities.invokeLater {
//        val frame = JFrame("Low-Memory Parser")
//        val textArea = JTextArea()
//        val statusLabel = JLabel("Ready")
//
//        // CoroutineScope tied to the frame
//        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
//        var currentJob: Job? = null
//
//        // Real-time parsing with debounce
//        val debounceDelay = 200L // ms
//        var lastChangeTime = 0L
//
//        textArea.document.addDocumentListener(object : DocumentListener {
//            override fun insertUpdate(e: DocumentEvent) = scheduleParsing()
//            override fun removeUpdate(e: DocumentEvent) = scheduleParsing()
//            override fun changedUpdate(e: DocumentEvent) = scheduleParsing()
//
//            fun scheduleParsing() {
//                currentJob?.cancel()
//                lastChangeTime = System.currentTimeMillis()
//
//                scope.launch {
//                    delay(debounceDelay)
//                    if (System.currentTimeMillis() - lastChangeTime < debounceDelay) return@launch
//
//                    // 1. Acquire buffer from pool
//                    val text = textArea.text
//                    val buffer = CharBufferPool.acquire(text.length)
//                    text.toCharArray(buffer, 0, 0, text.length)
//
//                    // 2. Create lightweight snapshot
//                    val snapshot = TextSnapshot(
//                        buffer = buffer,
//                        length = text.length,
//                        version = lastChangeTime
//                    )
//
//                    // 3. Process and release buffer
//                    try {
//                        val result = parseText(snapshot)
//                        withContext(Dispatchers.Swing) {
//                            if (snapshot.version == lastChangeTime) {
//                                updateUI(result)
//                            }
//                        }
//                    } finally {
//                        CharBufferPool.release(buffer)
//                    }
//                }.also { currentJob = it }
//            }
//        })
//
//        frame.add(JScrollPane(textArea))
//        frame.add(statusLabel, "South")
//        frame.setSize(500, 300)
//        frame.isVisible = true
//    }
//}
//
//// Immutable snapshot wrapping pooled buffer
//data class TextSnapshot(
//    private val buffer: CharArray,
//    val length: Int,
//    val version: Long
//) {
//    // Access via read-only iterator to prevent modifications
//    fun iterator(): CharIterator = object : CharIterator() {
//        private var index = 0
//        override fun hasNext() = index < length
//        override fun nextChar() = buffer[index++]
//    }
//}
//
//// Memory-efficient parsing
//suspend fun parseText(snapshot: TextSnapshot): ParsedResult = withContext(Dispatchers.Default) {
//    var isValid = true
//    val iterator = snapshot.iterator()
//    val chunkSize = 1000
//    val chunk = CharArray(chunkSize)
//
//    while (iterator.hasNext()) {
//        var chunkLength = 0
//        while (chunkLength < chunkSize && iterator.hasNext()) {
//            chunk[chunkLength++] = iterator.nextChar()
//        }
//
//        // Process chunk (e.g., validate syntax)
//        isValid = isValid && chunk.contains('v') // Simplified check
//        ensureActive()
//        yield()
//    }
//
//    ParsedResult(isValid)
//}
//
//data class ParsedResult(val isValid: Boolean)
//
//
//
//
