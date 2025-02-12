package org.editor.presentation.components.scrollpane

import org.editor.application.common.UserCaret
import org.editor.presentation.components.textpane.CaretListener
import org.editor.presentation.components.textpane.EditorTheme
import org.editor.presentation.components.textpane.KTextPane
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JComponent
import javax.swing.JScrollBar
import kotlin.math.max


/**
 * A custom scroll pane that holds the KTextPane.
 */
class KScrollPane(
    private val content: KTextPane,
    private val editorTheme: EditorTheme
) : JComponent() {
    private val horizontalScrollBar = JScrollBar(JScrollBar.HORIZONTAL)
    private val verticalScrollBar = JScrollBar(JScrollBar.VERTICAL)
    private val viewport = Rectangle()

    private var oldWidth = -1
    private var oldHeight = -1

    init {
        layout = null
        add(horizontalScrollBar)
        add(verticalScrollBar)
        add(content)

        // --- Horizontal scrollbar listener (pixel-based) ---
        horizontalScrollBar.addAdjustmentListener { e ->
            val newScrollX = e.value
            content.scrollX = newScrollX
            content.scheduleFullRepaint()
        }

        // --- Vertical scrollbar listener (pixel-based) ---
        verticalScrollBar.addAdjustmentListener { e ->
            val newScrollY = e.value
            content.scrollY = newScrollY
            content.scheduleFullRepaint()
        }

        addMouseWheelListener { e ->
            handleMouseWheelScroll(e)
        }

        // Defer re-layout after content is resized
        content.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                updateScrollPane()
            }
        })

        content.addCaretListener(object : CaretListener {
            override fun caretMoved(newCaret: UserCaret) {
                ensureCaretVisible(newCaret)
            }
        })
    }

    // --------------------- Ensure Caret Visibility ---------------------
    private fun ensureCaretVisible(caret: UserCaret) {
        // Calculate caret's pixel position
        val caretX = getCaretX(caret)
        val caretY = caret.row * content.fontMetrics.height

        val visibleWidth = viewport.width
        val visibleHeight = viewport.height

        val currentScrollX = content.scrollX
        val currentScrollY = content.scrollY

        var desiredScrollX = currentScrollX
        var desiredScrollY = currentScrollY

        // Horizontal
        if (caretX < currentScrollX + editorTheme.horizontalPadding) {
            desiredScrollX = (caretX - editorTheme.horizontalPadding).coerceAtLeast(0)
        } else if (caretX > currentScrollX + visibleWidth - editorTheme.horizontalPadding) {
            desiredScrollX = (caretX - visibleWidth + editorTheme.horizontalPadding).coerceAtMost(getMaxScrollX())
        }

        // Vertical
        if (caretY < currentScrollY + editorTheme.horizontalPadding) {
            desiredScrollY = (caretY - editorTheme.horizontalPadding).coerceAtLeast(0)
        } else if (caretY > currentScrollY + visibleHeight - editorTheme.horizontalPadding) {
            desiredScrollY = (caretY - visibleHeight + editorTheme.horizontalPadding).coerceAtMost(getMaxScrollY())
        }

        // Update scrollbars if needed
        var scrollChanged = false
        if (desiredScrollX != currentScrollX) {
            horizontalScrollBar.value = desiredScrollX
            scrollChanged = true
        }
        if (desiredScrollY != currentScrollY) {
            verticalScrollBar.value = desiredScrollY
            scrollChanged = true
        }

        if (scrollChanged) {
            content.scheduleFullRepaint()
        }
    }

    private fun getCaretX(caret: UserCaret): Int {
        val lineText = content.textPane.getText(caret.row)
        val textUpToCaret = if (caret.col <= lineText.length) {
            lineText.substring(0, caret.col)
        } else {
            lineText
        }

        return (editorTheme.gutterWidth + editorTheme.horizontalPadding) +
                content.fontMetrics.stringWidth(textUpToCaret)
    }

    private fun getMaxScrollX(): Int {
        val maxLineWidth = content.textPane.getMaxLineWidth()
        return (maxLineWidth - viewport.width).coerceAtLeast(0)
    }

    private fun getMaxScrollY(): Int {
        val totalHeight = content.textPane.rows() * content.fontMetrics.height
        return (totalHeight - viewport.height).coerceAtLeast(0)
    }

    /**
     * Mouse wheel scroll:
     *  - If Shift is down, do horizontal pixel-based scrolling.
     *  - Otherwise, do vertical pixel-based scrolling.
     */
    private fun handleMouseWheelScroll(e: MouseWheelEvent) {
        if (e.isShiftDown) {
            // Horizontal scrolling (pixel-based)
            val delta = (e.preciseWheelRotation * horizontalScrollBar.unitIncrement).toInt()
            val newScrollX = (horizontalScrollBar.value + delta).coerceIn(
                horizontalScrollBar.minimum,
                horizontalScrollBar.maximum - horizontalScrollBar.visibleAmount
            )
            horizontalScrollBar.value = newScrollX
            content.scrollX = newScrollX
            content.scheduleFullRepaint()
        } else {
            // Vertical scrolling (pixel-based)
            val delta = (e.preciseWheelRotation * verticalScrollBar.unitIncrement).toInt()
            val newScrollY = (verticalScrollBar.value + delta).coerceIn(
                verticalScrollBar.minimum,
                verticalScrollBar.maximum - verticalScrollBar.visibleAmount
            )
            verticalScrollBar.value = newScrollY
            content.scrollY = newScrollY
            content.scheduleFullRepaint()
        }

    }

    override fun doLayout() {
        val scrollbarWidth = verticalScrollBar.preferredSize.width // Typically 15
        val scrollbarHeight = horizontalScrollBar.preferredSize.height // Typically 15

        // Initial viewport dimensions without considering both scrollbars
        var viewportWidth = width - scrollbarWidth
        var viewportHeight = height - scrollbarHeight

        // Determine if scrollbars are needed based on content size
        val preferredSize = getCachedPreferredSize()
        val horizontalNeeded = preferredSize.width > viewportWidth
        val verticalNeeded = preferredSize.height > viewportHeight

        // Adjust viewport dimensions if both scrollbars are needed
        if (horizontalNeeded && verticalNeeded) {
            viewportWidth -= scrollbarWidth
            viewportHeight -= scrollbarHeight
        } else if (horizontalNeeded) {
            // Only horizontal scrollbar is needed
            // No additional adjustment required
        } else if (verticalNeeded) {
            // Only vertical scrollbar is needed
            // No additional adjustment required
        }

        // Recalculate based on updated needs
        viewportWidth = width - if (verticalNeeded) scrollbarWidth else 0
        viewportHeight = height - if (horizontalNeeded) scrollbarHeight else 0

        viewport.setBounds(0, 0, viewportWidth, viewportHeight)

        // Determine content size
        val contentWidth = max(preferredSize.width, viewportWidth)
        val contentHeight = max(preferredSize.height, viewportHeight)
        // Position the content
        content.setBounds(0, 0, contentWidth, contentHeight)

        // Configure horizontal scrollbar
        horizontalScrollBar.isVisible = horizontalNeeded
        if (horizontalNeeded) {
            horizontalScrollBar.setBounds(0, height - scrollbarHeight, viewportWidth, scrollbarHeight)
            horizontalScrollBar.minimum = 0
            horizontalScrollBar.maximum = preferredSize.width
            horizontalScrollBar.visibleAmount = viewportWidth
            horizontalScrollBar.unitIncrement = 50
            horizontalScrollBar.blockIncrement = viewportWidth
            horizontalScrollBar.value = content.scrollX.coerceIn(
                horizontalScrollBar.minimum,
                horizontalScrollBar.maximum - horizontalScrollBar.visibleAmount
            )
        }

        // Configure vertical scrollbar
        verticalScrollBar.isVisible = verticalNeeded
        if (verticalNeeded) {
            verticalScrollBar.setBounds(width - scrollbarWidth, 0, scrollbarWidth, viewportHeight)
            verticalScrollBar.minimum = 0
            verticalScrollBar.maximum = preferredSize.height
            verticalScrollBar.visibleAmount = viewportHeight
            verticalScrollBar.unitIncrement = 50
            verticalScrollBar.blockIncrement = viewportHeight
            verticalScrollBar.value = content.scrollY.coerceIn(
                verticalScrollBar.minimum,
                verticalScrollBar.maximum - verticalScrollBar.visibleAmount
            )
        }

        content.scheduleFullRepaint()

    }

    private fun getCachedPreferredSize(): Dimension {
        return content.preferredSize
    }

    fun updateScrollPane() {
        val newWidth = viewport.width
        val newHeight = viewport.height

        if (newWidth != oldWidth || newHeight != oldHeight) {
            oldWidth = newWidth
            oldHeight = newHeight

            val viewportWidth = viewport.width
            val viewportHeight = viewport.height

            // Re-lay out the content
            val contentWidth = max(newWidth, viewportWidth)
            val contentHeight = max(newHeight, viewportHeight)

            content.setBounds(0, 0, contentWidth, contentHeight)

            content.setViewportSize(viewport.width, viewport.height)

            // Update horizontal scrollbar
            horizontalScrollBar.isVisible = (contentWidth > viewportWidth)
            if (horizontalScrollBar.isVisible) {
                horizontalScrollBar.maximum = contentWidth
                horizontalScrollBar.visibleAmount = viewportWidth
                horizontalScrollBar.unitIncrement = 50
                horizontalScrollBar.blockIncrement = viewportWidth
                horizontalScrollBar.value = content.scrollX.coerceIn(
                    horizontalScrollBar.minimum,
                    horizontalScrollBar.maximum - horizontalScrollBar.visibleAmount
                )
            }

            // Update vertical scrollbar
            verticalScrollBar.isVisible = (contentHeight > viewportHeight)
            if (verticalScrollBar.isVisible) {
                verticalScrollBar.maximum = contentHeight
                verticalScrollBar.visibleAmount = viewportHeight
                verticalScrollBar.unitIncrement = 50
                verticalScrollBar.blockIncrement = viewportHeight
                verticalScrollBar.value = content.scrollY.coerceIn(
                    verticalScrollBar.minimum,
                    verticalScrollBar.maximum - verticalScrollBar.visibleAmount
                )
            }

            content.scheduleFullRepaint()
            revalidate()
        }

    }
}