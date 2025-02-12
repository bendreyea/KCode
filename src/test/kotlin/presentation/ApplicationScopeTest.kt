package presentation

import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.editor.ApplicationScope
import javax.swing.SwingUtilities
import kotlin.test.Test

class ApplicationScopeTest {

    @Test
    fun `exception triggers UI error dialog`() = runTest {
        // Mock showErrorDialog function
        mockkObject(ApplicationScope)
        every { ApplicationScope.showErrorDialog(any()) } just Runs

        // Launch a coroutine that throws an exception
        ApplicationScope.scope.launch {
            throw RuntimeException("Test exception")
        }

        // Allow time for Swing's invokeLater to execute
        delay(100)  // Ensures the Swing event loop processes the callback
        SwingUtilities.invokeAndWait {} // Forces pending UI tasks to run

        // Verify that the dialog function was called
        verify { ApplicationScope.showErrorDialog("Unhandled exception: Test exception") }

        unmockkObject(ApplicationScope) // Clean up mock
    }
}
