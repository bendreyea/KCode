package org.editor.presentation.components.textpane

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.editor.application.Caret
import org.editor.presentation.components.SwingDispatchers
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import kotlin.math.max
import kotlin.math.min

class KTextPane(
    val textPane: TextPaneContent,
    private val theme: EditorTheme,
    private val mainScope: CoroutineScope
) : JPanel() {

    // Lazy font metrics
    private val fontMetricsLazy by lazy { getFontMetrics(font) }
    private val lineHeightLazy by lazy { fontMetricsLazy.height }

    val fontMetrics: FontMetrics get() = fontMetricsLazy
    private val lineHeight: Int get() = lineHeightLazy

    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0

    // Repaint coalescing fields
    private var repaintPending = false
    private var dirtyRegion: Rectangle? = null

    private val controller: KTextEditorController
    private val painter: KTextEditorPainter


    var scrollX: Int = 0
        set(value) {
            field = value
            controller.scrollX = value
        }
        get() = controller.scrollX

    var scrollY: Int = 0
        set(value) {
            field = value
            controller.scrollY = value
        }
        get() = controller.scrollY

    init {
        isFocusable = true
        focusTraversalKeysEnabled = false

        val repaintCallback: (Caret, Caret) -> Unit = { begin, end ->
            scheduleRepaintLines(begin, end)
        }

        val fullRepaintCallback: () -> Unit = {
            scheduleIncrementalRepaint()
        }

        val getHeight: () -> Int = { viewportHeight }
        val getWidth: () -> Int = { viewportWidth }

        controller = KTextEditorController(
            textPane, fontMetrics, theme,
            getHeight, getWidth,
            repaintCallback, fullRepaintCallback
        )

        painter = KTextEditorPainter(textPane, controller, fontMetrics, theme)

        addKeyListener(controller)
        addMouseListener(controller)
        addMouseMotionListener(controller)

        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent?) {
                requestFocusInWindow()
            }
        })
    }

    fun addCaretListener(listener: CaretListener) {
        controller.addCaretListener(listener)
    }

    /**
     * Instead of parsing here, we only do a repaint. Parsing is done in the MainFrame after changes.
     */
    private fun scheduleRepaintLines(begin: Caret, end: Caret) {
        val (start, finish) = if (begin < end) begin to end else end to begin
        val startRow = start.row
        val endRow = finish.row

        // Compute the actual on-screen rectangle that changed
        val visibleStartRow = max(0, controller.scrollY / lineHeight)
        val visibleEndRow = min(textPane.rows(), (controller.scrollY + viewportHeight) / lineHeight)

        val repaintStartRow = max(startRow, visibleStartRow)
        val repaintEndRow = min(endRow, visibleEndRow)

        if (repaintStartRow <= repaintEndRow) {
            val y = repaintStartRow * lineHeight - controller.scrollY
            val h = (repaintEndRow - repaintStartRow + 1) * lineHeight
            scheduleRepaint(Rectangle(0, y, width, h))
        }
    }

    /**
     * Full incremental repaint of the visible region.
     */
    fun scheduleIncrementalRepaint() {
        // Visible rows
        val visibleStartRow = max(0, scrollY / lineHeight)
        val visibleEndRow = min(textPane.rows(), (scrollY + viewportHeight) / lineHeight)

        val repaintStartY = visibleStartRow * lineHeight - scrollY
        val repaintEndY = (visibleEndRow + 1) * lineHeight - scrollY
        val repaintHeight = repaintEndY - repaintStartY
        println("repaint visible region: $visibleStartRow, $visibleEndRow")
        scheduleRepaint(Rectangle(0, repaintStartY, width, repaintHeight))
    }

    /**
     * Full repaint of the visible region.
     */
    fun scheduleFullRepaint() {
        // Visible rows
        val visibleStartRow = max(0, scrollY / lineHeight)
        val visibleEndRow = min(textPane.rows(), (scrollY + viewportHeight) / lineHeight)

        val repaintStartY = visibleStartRow * lineHeight - scrollY
        val repaintEndY = (visibleEndRow + 1) * lineHeight - scrollY
        val repaintHeight = repaintEndY - repaintStartY

        scheduleRepaint(Rectangle(0, repaintStartY, width, repaintHeight))
    }

    /**
     * Coalesce multiple repaint requests into one EDT invocation.
     */
    private fun scheduleRepaint(rect: Rectangle) {
        if (dirtyRegion == null) {
            dirtyRegion = rect
        } else {
            dirtyRegion = dirtyRegion!!.union(rect)
        }

        if (!repaintPending) {
            repaintPending = true
            mainScope.launch {
                withContext(SwingDispatchers.Swing) {
                    dirtyRegion?.let { region ->
                        repaint(region.x, region.y, region.width, region.height)
                    }
                    dirtyRegion = null
                    repaintPending = false
                }
            }
        }
    }

    fun setViewportSize(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        scheduleFullRepaint()
    }

    override fun getPreferredSize(): Dimension {
        // Instead of scanning all lines each time, we’ll retrieve a cached max line width
        val rows = maxOf(1, textPane.rows())
        val height = rows * lineHeight + 20
        val width = maxOf(
            600,
            textPane.getMaxLineWidth() + theme.gutterWidth + theme.horizontalPadding * 2
        )
        return Dimension(width, height)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        painter.paint(g as Graphics2D, viewportWidth, viewportHeight)
    }

    fun getTextContent(): String = textPane.getTextContent()

    fun setTextContent(content: String) {
        // Clear then insert new content
        textPane.replace(0, 0, getTextContent().length, content)
        // Reset caret & selection
        controller.clearSelection()
        // Update scroll
        mainScope.launch {
            withContext(SwingDispatchers.Swing) {
                scrollX = 0
                scrollY = 0
                scheduleFullRepaint()
                revalidate()
            }
        }
    }
}

