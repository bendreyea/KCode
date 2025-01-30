package org.editor

import kotlinx.coroutines.*
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

object ApplicationScope {
    private val supervisorJob = SupervisorJob()
    private val exceptionHandler = CoroutineExceptionHandler { _, e ->
        SwingUtilities.invokeLater {
            // Handle exceptions in UI thread
            showErrorDialog("Unhandled exception: ${e.message}")
        }
    }

    val scope = CoroutineScope(
        supervisorJob +
                Dispatchers.Default +
                exceptionHandler
    )

    fun dispose() {
        supervisorJob.cancel("Application shutdown")
    }

    private fun showErrorDialog(message: String) {
        JOptionPane.showMessageDialog(
            null,
            message,
            "Error",
            JOptionPane.ERROR_MESSAGE
        )
    }
}