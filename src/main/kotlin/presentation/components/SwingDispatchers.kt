package org.editor.presentation.components

import kotlinx.coroutines.CoroutineDispatcher
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext

// Extension to provide Dispatchers.Swing
object SwingDispatchers {
    val Swing: CoroutineDispatcher = SwingDispatcher

    private object SwingDispatcher : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (SwingUtilities.isEventDispatchThread()) {
                block.run()
            } else {
                SwingUtilities.invokeLater(block)
            }
        }
    }
}